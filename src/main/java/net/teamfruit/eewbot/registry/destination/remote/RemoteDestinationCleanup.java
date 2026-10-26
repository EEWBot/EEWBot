package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.registry.destination.DestinationCleanup;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Best effort, nonpersistent queue; snapshot changes are left to RevisionPoller. */
public final class RemoteDestinationCleanup implements DestinationCleanup, AutoCloseable {
    public static final int MAX_TARGETS = 500;
    public static final int QUEUE_CAPACITY = 256;
    private static final Pattern WEBHOOK = Pattern.compile("https://discord\\.com/api/webhooks/([1-9][0-9]{0,18})/[A-Za-z0-9_-]+(?:\\?[^#\\s]*)?");
    private final RemoteCleanupApiClient client;
    private final ThreadPoolExecutor worker;
    private final long shutdownMillis;
    private final long countWaitMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RemoteDestinationCleanup(RemoteCleanupApiClient client) {
        this(client, QUEUE_CAPACITY, 10000, 10000);
    }

    RemoteDestinationCleanup(RemoteCleanupApiClient client, int capacity, long shutdownMillis, long countWaitMillis) {
        this.client = client;
        this.shutdownMillis = shutdownMillis;
        this.countWaitMillis = countWaitMillis;
        this.worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity),
                r -> new Thread(r, "eewbot-remote-cleanup"), new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public void guildDeleted(long guildId) {
        enqueue("guild", Map.of("guildId", id(guildId)));
    }

    @Override public void destinationDeleted(long targetId) { destinationsUnavailable(List.of(targetId)); }

    @Override public void destinationsUnavailable(Collection<Long> targetIds) {
        batches("destinations", "targetIds", targetIds.stream().map(RemoteDestinationCleanup::id).distinct().toList());
    }

    @Override public void webhooksUnavailable(Collection<Long> targetIds) {
        // Never fall back to unguarded target cleanup when the expected ID is missing.
        Log.logger.warn("Remote webhook cleanup NOT applied: expected webhook IDs missing (count={})", targetIds.size());
    }

    @Override public void webhooksUnavailable(Map<Long, DeliveryTarget> failedTargets) {
        List<Map<String, String>> targets = new ArrayList<>();
        for (var entry : failedTargets.entrySet()) {
            String webhookId = webhookId(entry.getValue().webhookUrl());
            if (webhookId != null) targets.add(Map.of("targetId", id(entry.getKey()), "expectedWebhookId", webhookId));
            else Log.logger.warn("Remote webhook cleanup NOT applied: invalid webhook reference (count=1)");
        }
        batches("webhooks", "targets", targets);
    }

    /** Called only by scheduled Webhook Sender maintenance, never by Gateway listeners. */
    @Override public int webhookUrlsUnavailable(Collection<String> webhookUrls) {
        var ids = new LinkedHashSet<String>();
        for (String url : webhookUrls) {
            String webhookId = webhookId(url);
            if (webhookId != null) ids.add(webhookId);
            else Log.logger.warn("Remote webhook cleanup skipped invalid URL (count=1)");
        }
        if (ids.isEmpty()) return 0;
        var values = List.copyOf(ids);
        int affected = 0;
        for (int start = 0; start < values.size(); start += MAX_TARGETS) {
            Object body = Map.of("webhookIds", values.subList(start, Math.min(start + MAX_TARGETS, values.size())));
            Future<Integer> result;
            try { result = this.worker.submit(() -> this.client.mutate("webhook-ids", body)); }
            catch (RejectedExecutionException e) {
                Log.logger.warn("Remote cleanup queue full or closed; operation NOT applied");
                throw new RemoteConfigException("Remote cleanup queue full or closed");
            }
            try { affected += result.get(this.countWaitMillis, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) {
                result.cancel(true);
                Thread.currentThread().interrupt();
                throw new RemoteConfigException("Remote cleanup count interrupted; outcome may be unknown");
            } catch (ExecutionException | TimeoutException | CancellationException e) {
                result.cancel(true);
                throw new RemoteConfigException("Remote cleanup count unavailable; outcome may be unknown; operation not retried");
            }
        }
        return affected;
    }

    private void batches(String operation, String field, List<?> values) {
        for (int start = 0; start < values.size(); start += MAX_TARGETS) {
            enqueue(operation, Map.of(field, List.copyOf(values.subList(start, Math.min(start + MAX_TARGETS, values.size())))));
        }
    }

    private void enqueue(String operation, Object body) {
        try {
            this.worker.execute(() -> {
                try {
                    int affected = this.client.mutate(operation, body);
                    Log.logger.info("Remote cleanup completed: operation={}, affected={}", operation, affected);
                } catch (RemoteConfigException e) {
                    Log.logger.warn("Remote cleanup failed: operation={}, reason={}", operation, e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            Log.logger.warn("Remote cleanup queue full or closed; operation NOT applied: operation={}", operation);
        }
    }

    private static String id(long value) {
        if (value <= 0) throw new RemoteConfigException("Invalid cleanup Snowflake");
        return Long.toString(value);
    }

    static String webhookId(String url) {
        if (url == null) return null;
        var match = WEBHOOK.matcher(url);
        if (!match.matches()) return null;
        try { return id(Long.parseLong(match.group(1))); }
        catch (RuntimeException e) { return null; }
    }

    @Override public void save() throws IOException { close(); }

    @Override public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        this.worker.shutdown();
        try {
            if (!this.worker.awaitTermination(this.shutdownMillis, TimeUnit.MILLISECONDS)) {
                int pending = this.worker.getQueue().size() + this.worker.getActiveCount();
                Log.logger.warn("Remote cleanup shutdown expired with {} unfinished operation(s); outcome may be unknown", pending);
                cancelPending();
            }
        } catch (InterruptedException e) {
            Log.logger.warn("Remote cleanup shutdown interrupted with unfinished operations");
            cancelPending();
            Thread.currentThread().interrupt();
        } finally { this.client.close(); }
    }

    private void cancelPending() {
        for (Runnable task : this.worker.shutdownNow()) {
            if (task instanceof Future<?> future) future.cancel(false);
        }
    }
}
