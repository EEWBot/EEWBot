package net.teamfruit.eewbot.quakeinfo;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.registry.config.ConfigV2;

import java.util.concurrent.*;

/**
 * One coalescing publisher; gateway callers only set a flag and schedule work.
 * Publishes on start and on every Store update; a failed send stays dirty until it succeeds.
 */
public final class QuakeInfoSnapshotPublisher implements AutoCloseable {
    private final QuakeInfoStore store;
    private final EmbedContext context;
    private final QuakeInfoSnapshotClient client;
    private final ConfigV2.QuakeInfoPublisher config;
    private final ScheduledExecutorService executor;
    private final Runnable listener = this::requestSync;
    private ScheduledFuture<?> task;
    private boolean started;
    private boolean running;
    private boolean dirty;
    private boolean closed;
    // The following state is confined to the single executor.
    private long retryAt = System.nanoTime();
    private long retryDelay;
    private String lastFingerprint;
    private String rejectedFingerprint;

    public QuakeInfoSnapshotPublisher(QuakeInfoStore store, EmbedContext context, QuakeInfoSnapshotClient client,
                                      ConfigV2.QuakeInfoPublisher config) {
        if (config.getRetryInitialMillis() < 10 || config.getRetryMaxMillis() < config.getRetryInitialMillis()
                || config.getRetryMaxMillis() > 3600000)
            throw new IllegalArgumentException("Invalid QuakeInfo publisher intervals");
        this.store = store;
        this.context = context;
        this.client = client;
        this.config = config;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "eewbot-quakeinfo-publisher"));
    }

    public synchronized void start() {
        if (this.closed || this.started) return;
        this.started = true;
        this.store.addUpdateListener(this.listener);
        requestSync();
    }

    public synchronized void requestSync() {
        if (this.closed) return;
        this.dirty = true;
        schedule();
    }
    private synchronized void schedule() {
        if (this.closed || this.running || this.task != null || !this.dirty) return;
        long delay = Math.max(0, this.retryAt - System.nanoTime());
        this.task = this.executor.schedule(this::run, delay, TimeUnit.NANOSECONDS);
    }

    private void run() {
        synchronized (this) {
            this.task = null;
            if (this.closed) return;
            this.running = true;
            this.dirty = false;
        }
        try {
            synchronizeLatest();
            this.retryDelay = 0;
            this.retryAt = System.nanoTime();
        } catch (QuakeInfoSnapshotClient.Failure e) {
            failed();
            Log.logger.warn("QuakeInfo publisher synchronization failed: status={}, code={}", e.status(), e.code());
        } catch (RuntimeException e) {
            // Renderer/transport exceptions may include input data or secrets. Never log the exception.
            failed();
            Log.logger.warn("QuakeInfo publisher rendering or synchronization failed; retry scheduled");
        } finally {
            synchronized (this) { this.running = false; schedule(); }
        }
    }

    private void synchronizeLatest() {
        long version = this.store.getUpdateVersion();
        var latest = this.store.getLatestReport();
        if (latest.isEmpty()) return;
        QuakeInfoSnapshot rendered = QuakeInfoSnapshot.render(latest.get(), this.context);
        if (this.store.getUpdateVersion() != version) { requestSync(); return; }
        String fingerprint = rendered.fingerprint();
        if (fingerprint.equals(this.rejectedFingerprint) || fingerprint.equals(this.lastFingerprint)) return;
        try {
            this.client.publish(rendered);
        } catch (QuakeInfoSnapshotClient.Failure e) {
            if (e.retryable()) throw e;
            // Resending identical bytes cannot fix a 4xx. Wait for the rendered content to change.
            this.rejectedFingerprint = fingerprint;
            Log.logger.warn("QuakeInfo publisher request rejected: status={}, code={}", e.status(), e.code());
            return;
        }
        // accepted=false only means the stored latest is strictly newer; nothing to retry either way.
        this.lastFingerprint = fingerprint;
        this.rejectedFingerprint = null;
    }

    private synchronized void failed() {
        this.retryDelay = this.retryDelay == 0 ? this.config.getRetryInitialMillis()
                : Math.min(this.config.getRetryMaxMillis(), this.retryDelay * 2);
        this.retryAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(this.retryDelay);
        this.dirty = true;
    }

    @Override public void close() {
        synchronized (this) {
            if (this.closed) return;
            this.closed = true;
            this.store.removeUpdateListener(this.listener);
            this.executor.shutdownNow();
        }
        this.client.close();
        try { this.executor.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
