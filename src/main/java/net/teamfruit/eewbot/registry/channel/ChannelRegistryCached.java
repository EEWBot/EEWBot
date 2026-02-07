package net.teamfruit.eewbot.registry.channel;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Cached implementation of ChannelRegistry.
 * <p>
 * Delivery path methods use an in-memory snapshot for zero-DB-access.
 * Write methods update the DB and trigger snapshot reload.
 * Single channel lookups use Caffeine cache.
 */
public class ChannelRegistryCached implements ChannelRegistry {

    private static final int MAX_CACHE_SIZE = 10000;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ChannelRegistrySql delegate;
    private final DeliverySnapshotLoader snapshotLoader;
    private final ConfigRevisionStore revisionStore;
    private final AtomicReference<DeliverySnapshot> snapshotRef;
    private final Cache<Long, Channel> channelCache;
    private final ExecutorService reloadExecutor;
    private final AtomicBoolean reloadScheduled = new AtomicBoolean(false);

    public ChannelRegistryCached(
            ChannelRegistrySql delegate,
            DeliverySnapshotLoader snapshotLoader,
            ConfigRevisionStore revisionStore,
            ExecutorService reloadExecutor
    ) {
        this.delegate = delegate;
        this.snapshotLoader = snapshotLoader;
        this.revisionStore = revisionStore;
        this.reloadExecutor = reloadExecutor;
        this.snapshotRef = new AtomicReference<>();
        this.channelCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    /**
     * Initialize the snapshot. Must be called before using delivery path methods.
     * Blocks until snapshot is loaded. Throws exception on failure (fail-fast).
     */
    public void initializeSnapshot() {
        Log.logger.info("Initializing delivery snapshot...");
        DeliverySnapshot snapshot = this.snapshotLoader.loadInTransaction();
        this.snapshotRef.set(snapshot);
        Log.logger.info("Delivery snapshot initialized: {} channels, revision {}",
                snapshot.size(), snapshot.getRevision());
    }

    /**
     * Get the current snapshot (for RevisionPoller).
     */
    public DeliverySnapshot getSnapshot() {
        return this.snapshotRef.get();
    }

    /**
     * Request a snapshot reload. Coalesces multiple requests into one.
     * Used by both internal writes and RevisionPoller.
     */
    public void requestReload() {
        if (this.reloadScheduled.compareAndSet(false, true)) {
            this.reloadExecutor.execute(this::reloadTask);
        }
    }

    private void reloadTask() {
        try {
            reloadSnapshotLoop();
        } catch (Exception e) {
            Log.logger.error("Error reloading snapshot", e);
        } finally {
            this.reloadScheduled.set(false);
        }
        // Check if another reload is needed after completion
        try {
            long latest = this.revisionStore.getRevision();
            DeliverySnapshot cur = this.snapshotRef.get();
            if (cur == null || cur.getRevision() < latest) {
                requestReload();
            }
        } catch (Exception e) {
            Log.logger.error("Error checking revision after reload", e);
        }
    }

    private void reloadSnapshotLoop() {
        DeliverySnapshot current = this.snapshotRef.get();
        DeliverySnapshot loaded = this.snapshotLoader.loadInTransaction();
        if (current != null && current.getRevision() >= loaded.getRevision()) {
            return; // Already up to date
        }
        this.snapshotRef.set(loaded);
        Log.logger.debug("Snapshot reloaded: {} channels, revision {}",
                loaded.size(), loaded.getRevision());
    }

    // ========================================
    // Delivery path methods - use snapshot only, NEVER call delegate
    // ========================================

    @Override
    public Map<Boolean, Map<Long, ChannelBase>> getChannelsPartitionedByWebhookPresent(ChannelFilter filter) {
        DeliverySnapshot snapshot = this.snapshotRef.get();
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot not initialized. Call initializeSnapshot() first.");
        }
        return snapshot.getPartitionedByWebhook(filter);
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        DeliverySnapshot snapshot = this.snapshotRef.get();
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot not initialized. Call initializeSnapshot() first.");
        }
        return snapshot.getWebhookAbsentChannels();
    }

    @Override
    public List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
        DeliverySnapshot snapshot = this.snapshotRef.get();
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot not initialized. Call initializeSnapshot() first.");
        }
        return snapshot.getWebhookAbsentChannels(filter);
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long targetId) {
        DeliverySnapshot snapshot = this.snapshotRef.get();
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot not initialized. Call initializeSnapshot() first.");
        }
        return snapshot.isWebhookForThread(webhookId, targetId);
    }

    // ========================================
    // Single channel lookup - use Caffeine cache
    // ========================================

    @Override
    public Channel get(long key) {
        return this.channelCache.get(key, this.delegate::get);
    }

    @Override
    public boolean exists(long key) {
        return this.delegate.exists(key);
    }

    // ========================================
    // Write methods - update DB with revision++ in same transaction, then reload
    // ========================================

    @Override
    public void remove(long key) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.removeWithDsl(tx, key);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.channelCache.invalidate(key);
        requestReload();
    }

    @Override
    public void computeIfAbsent(long key, Function<? super Long, ? extends Channel> mappingFunction) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.computeIfAbsentWithDsl(tx, key, mappingFunction);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.channelCache.invalidate(key);
        requestReload();
    }

    @Override
    public void put(long key, Channel channel) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.putWithDsl(tx, key, channel);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.channelCache.invalidate(key);
        requestReload();
    }

    @Override
    public void set(long key, String name, boolean bool) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setWithDsl(tx, key, name, bool);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.channelCache.invalidate(key);
        requestReload();
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setMinIntensityWithDsl(tx, key, intensity);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.channelCache.invalidate(key);
        requestReload();
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setWebhookWithDsl(tx, key, webhook);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.channelCache.invalidate(key);
        requestReload();
    }

    @Override
    public void setLang(long key, String lang) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setLangWithDsl(tx, key, lang);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.channelCache.invalidate(key);
        requestReload();
    }

    // ========================================
    // Batch write methods - update DB with revision++ in same transaction, then reload
    // ========================================

    @Override
    public int removeByGuildId(long guildId) {
        AtomicInteger count = new AtomicInteger(0);
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            int removed = this.delegate.removeByGuildIdWithDsl(tx, guildId);
            count.set(removed);
            if (removed > 0) {
                this.revisionStore.incrementWithDsl(tx);
            }
        });
        if (count.get() > 0) {
            this.channelCache.invalidateAll();
            requestReload();
        }
        return count.get();
    }

    @Override
    public int clearWebhookByUrls(Collection<String> webhookUrls) {
        if (webhookUrls.isEmpty()) {
            return 0;
        }
        AtomicInteger count = new AtomicInteger(0);
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            int cleared = this.delegate.clearWebhookByUrlsWithDsl(tx, webhookUrls);
            count.set(cleared);
            if (cleared > 0) {
                this.revisionStore.incrementWithDsl(tx);
            }
        });
        if (count.get() > 0) {
            this.channelCache.invalidateAll();
            requestReload();
        }
        return count.get();
    }

    @Override
    public int setLangByGuildId(long guildId, String lang) {
        AtomicInteger count = new AtomicInteger(0);
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            int updated = this.delegate.setLangByGuildIdWithDsl(tx, guildId, lang);
            count.set(updated);
            if (updated > 0) {
                this.revisionStore.incrementWithDsl(tx);
            }
        });
        if (count.get() > 0) {
            this.channelCache.invalidateAll();
            requestReload();
        }
        return count.get();
    }

    @Override
    public Map<Long, Channel> getAllChannels() {
        return this.delegate.getAllChannels();
    }

    @Override
    public void save() throws IOException {
        // No-op for SQL-based registry
    }

    /**
     * Get the underlying SQL registry (for shutdown).
     */
    public ChannelRegistrySql getDelegate() {
        return this.delegate;
    }
}
