package net.teamfruit.eewbot.quakeinfo;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.registry.config.ConfigV2;

import java.util.UUID;
import java.util.concurrent.*;

/** One coalescing publisher; gateway callers only set a flag and schedule work. */
public final class QuakeInfoSnapshotPublisher implements AutoCloseable {
    private final QuakeInfoStore store;
    private final EmbedContext context;
    private final QuakeInfoSnapshotClient client;
    private final ConfigV2.QuakeInfoPublisher config;
    private final ScheduledExecutorService executor;
    private final String bootId = UUID.randomUUID().toString();
    private final Runnable listener = this::requestSync;
    private ScheduledFuture<?> task;
    private ScheduledFuture<?> periodic;
    private boolean running;
    private boolean dirty;
    private boolean reconcile;
    private boolean closed;
    private volatile boolean fenced;
    // The following state is confined to the single executor.
    private long epoch;
    private long sequence;
    private long retryAt = System.nanoTime();
    private long retryDelay;
    private QuakeInfoSnapshot pending;
    private boolean commitUncertain;
    private String lastFingerprint;
    private String lastPublishedId;
    private String rejectedFingerprint;

    public QuakeInfoSnapshotPublisher(QuakeInfoStore store, EmbedContext context, QuakeInfoSnapshotClient client,
                                      ConfigV2.QuakeInfoPublisher config) {
        if (config.getReconcileIntervalMillis() < 10 || config.getReconcileIntervalMillis() > 3600000
                || config.getRetryInitialMillis() < 10 || config.getRetryMaxMillis() < config.getRetryInitialMillis()
                || config.getRetryMaxMillis() > 3600000)
            throw new IllegalArgumentException("Invalid QuakeInfo publisher intervals");
        this.store = store;
        this.context = context;
        this.client = client;
        this.config = config;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "eewbot-quakeinfo-publisher"));
    }

    public synchronized void start() {
        if (this.closed || this.periodic != null) return;
        this.store.addUpdateListener(this.listener);
        this.periodic = this.executor.scheduleWithFixedDelay(() -> request(true),
                this.config.getReconcileIntervalMillis(), this.config.getReconcileIntervalMillis(), TimeUnit.MILLISECONDS);
        request(true);
    }

    public void requestSync() { request(false); }
    private synchronized void request(boolean reconcile) {
        if (this.closed || this.fenced) return;
        this.dirty = true;
        this.reconcile |= reconcile;
        schedule();
    }
    private synchronized void schedule() {
        if (this.closed || this.fenced || this.running || this.task != null || !this.dirty) return;
        long delay = Math.max(0, this.retryAt - System.nanoTime());
        this.task = this.executor.schedule(this::run, delay, TimeUnit.NANOSECONDS);
    }

    private void run() {
        boolean checkRemote;
        synchronized (this) {
            this.task = null;
            if (this.closed || this.fenced) return;
            this.running = true;
            this.dirty = false;
            checkRemote = this.reconcile;
            this.reconcile = false;
        }
        try {
            synchronizeLatest(checkRemote);
            this.retryDelay = 0;
            this.retryAt = System.nanoTime();
        } catch (QuakeInfoSnapshotClient.Failure e) {
            if (e.code().equals("publisher_fenced")) fence();
            else failed();
            Log.logger.warn("QuakeInfo publisher synchronization failed: status={}, code={}", e.status(), e.code());
        } catch (RuntimeException e) {
            // Renderer/transport exceptions may include input data or secrets. Never log the exception.
            failed();
            Log.logger.warn("QuakeInfo publisher rendering or synchronization failed; retry scheduled");
        } finally {
            synchronized (this) { this.running = false; schedule(); }
        }
    }

    private void synchronizeLatest(boolean reconcile) {
        if (this.epoch == 0) this.epoch = this.client.lease(this.bootId);
        long version = this.store.getUpdateVersion();
        var latest = this.store.getLatestReport();
        if (latest.isEmpty()) return;
        if (this.sequence >= 9007199254740991L) { fence(); return; }
        QuakeInfoSnapshot rendered = QuakeInfoSnapshot.render(latest.get(), this.context, this.epoch, this.sequence + 1);
        if (this.store.getUpdateVersion() != version) { requestSync(); return; }
        String fingerprint = rendered.sourceKey() + ":" + rendered.contentHash();
        if (fingerprint.equals(this.rejectedFingerprint)) return;
        if (fingerprint.equals(this.lastFingerprint) && this.pending == null) {
            if (!reconcile) return;
            var remote = this.client.latest();
            if (remote.publisherEpoch() > this.epoch) { fence(); return; }
            if (this.lastPublishedId.equals(remote.snapshotId()) && rendered.contentHash().equals(remote.contentHash())) return;
        }
        if (this.pending == null || !fingerprint.equals(this.pending.sourceKey() + ":" + this.pending.contentHash())) {
            this.pending = rendered;
            this.sequence++;
            this.commitUncertain = false;
        }
        if (this.commitUncertain) {
            var remote = this.client.latest();
            if (this.pending.snapshotId().equals(remote.snapshotId())) { accepted(fingerprint); return; }
            if (remote.publisherEpoch() > this.epoch) { fence(); return; }
            this.commitUncertain = false;
        }
        this.client.create(this.pending);
        for (var language : this.pending.messages().entrySet()) {
            for (int i = 0; i < language.getValue().size(); i++) {
                if (Thread.currentThread().isInterrupted()) return;
                this.client.put(this.pending.snapshotId(), language.getKey(), i, language.getValue().get(i));
            }
        }
        try {
            this.commitUncertain = true;
            this.client.commit(this.pending.snapshotId());
        } catch (QuakeInfoSnapshotClient.Failure e) {
            // Timeout may have happened after commit: check meta before deciding or retransmitting.
            var remote = this.client.latest();
            if (this.pending.snapshotId().equals(remote.snapshotId())) { accepted(fingerprint); return; }
            if (remote.publisherEpoch() > this.epoch || e.code().equals("publisher_fenced")) { fence(); return; }
            if (e.status() == 409) {
                // A newer report already exists. Do not fill D1 with repeated rejected copies.
                this.rejectedFingerprint = fingerprint;
                this.pending = null;
                this.commitUncertain = false;
                return;
            }
            throw e;
        }
        accepted(fingerprint);
    }

    private void accepted(String fingerprint) {
        this.lastPublishedId = this.pending.snapshotId();
        this.lastFingerprint = fingerprint;
        this.rejectedFingerprint = null;
        this.pending = null;
        this.commitUncertain = false;
    }
    private synchronized void failed() {
        this.retryDelay = this.retryDelay == 0 ? this.config.getRetryInitialMillis()
                : Math.min(this.config.getRetryMaxMillis(), this.retryDelay * 2);
        this.retryAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(this.retryDelay);
        this.dirty = true;
        this.reconcile = true;
    }
    private synchronized void fence() {
        this.fenced = true;
        if (this.periodic != null) this.periodic.cancel(false);
        Log.logger.warn("QuakeInfo publisher fenced by a newer publisher; restart only the intended active Core");
    }
    public boolean isFenced() { return this.fenced; }

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
