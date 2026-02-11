package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admin registry backed by SQL database.
 * No Caffeine cache - reads go directly to SQL.
 * Writes update DB with revision increment in same transaction, then trigger snapshot reload.
 */
public class SqlAdminRegistry implements DestinationAdminRegistry {

    private final ChannelRegistrySql delegate;
    private final ConfigRevisionStore revisionStore;
    private final Runnable onWrite;

    /**
     * @param delegate the underlying SQL registry
     * @param revisionStore the revision store for cache invalidation
     * @param onWrite callback invoked after writes (typically triggers snapshot reload)
     */
    public SqlAdminRegistry(
            ChannelRegistrySql delegate,
            ConfigRevisionStore revisionStore,
            Runnable onWrite
    ) {
        this.delegate = delegate;
        this.revisionStore = revisionStore;
        this.onWrite = onWrite;
    }

    /**
     * Get the underlying SQL registry (for shutdown).
     */
    public ChannelRegistrySql getDelegate() {
        return this.delegate;
    }

    // ========================================
    // Read methods - direct SQL, no cache
    // ========================================

    @Override
    public Channel get(long key) {
        return this.delegate.get(key);
    }

    @Override
    public boolean exists(long key) {
        return this.delegate.exists(key);
    }

    @Override
    public Map<Long, Channel> getAllChannels() {
        return this.delegate.getAllChannels();
    }

    @Override
    public List<Long> getWebhookAbsentChannels() {
        return this.delegate.getWebhookAbsentChannels();
    }

    @Override
    public List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
        return this.delegate.getWebhookAbsentChannels(filter);
    }

    @Override
    public boolean isWebhookForThread(long webhookId, long targetId) {
        return this.delegate.isWebhookForThread(webhookId, targetId);
    }

    // ========================================
    // Write methods - update DB with revision++ in same transaction, then notify
    // ========================================

    @Override
    public void remove(long key) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.removeWithDsl(tx, key);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    @Override
    public void put(long key, Channel channel) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.putWithDsl(tx, key, channel);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    @Override
    public void set(long key, String name, boolean bool) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setWithDsl(tx, key, name, bool);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    @Override
    public void setAll(long key, java.util.Map<String, Boolean> values) {
        if (values.isEmpty()) {
            return;
        }
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setAllWithDsl(tx, key, values);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setMinIntensityWithDsl(tx, key, intensity);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setWebhookWithDsl(tx, key, webhook);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    @Override
    public void setLang(long key, String lang) {
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.setLangWithDsl(tx, key, lang);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    @Override
    public void putAll(Map<Long, Channel> channels) {
        if (channels.isEmpty()) {
            return;
        }
        this.delegate.getDsl().transaction(ctx -> {
            org.jooq.DSLContext tx = ctx.dsl();
            this.delegate.putAllWithDsl(tx, channels);
            this.revisionStore.incrementWithDsl(tx);
        });
        this.onWrite.run();
    }

    // ========================================
    // Batch write methods
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
            this.onWrite.run();
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
            this.onWrite.run();
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
            this.onWrite.run();
        }
        return count.get();
    }
}
