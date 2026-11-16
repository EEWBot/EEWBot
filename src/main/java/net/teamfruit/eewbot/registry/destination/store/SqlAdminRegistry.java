package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelSettingKey;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.jooq.DSLContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

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
     * @param delegate      the underlying SQL registry
     * @param revisionStore the revision store for cache invalidation
     * @param onWrite       callback invoked after writes (typically triggers snapshot reload)
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

    /**
     * Runs a write in one transaction. When it changed any row, the revision is incremented in
     * the same transaction and the write callback fires after commit; a no-op write does neither.
     *
     * @return the number of rows the write affected
     */
    private int write(ToIntFunction<DSLContext> operation) {
        int affected = this.delegate.getDsl().transactionResult(ctx -> {
            DSLContext tx = ctx.dsl();
            int rows = operation.applyAsInt(tx);
            if (rows > 0) {
                this.revisionStore.incrementWithDsl(tx);
            }
            return rows;
        });
        if (affected > 0) {
            this.onWrite.run();
        }
        return affected;
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
    public boolean isWebhookExclusiveToTarget(long webhookId, long targetId) {
        return this.delegate.isWebhookExclusiveToTarget(webhookId, targetId);
    }

    // ========================================
    // Write methods - see write()
    // ========================================

    @Override
    public void remove(long key) {
        write(tx -> this.delegate.removeWithDsl(tx, key));
    }

    @Override
    public void put(long key, Channel channel) {
        write(tx -> this.delegate.insertChannelIfAbsentWithDsl(tx, key, channel));
    }

    @Override
    public void set(long key, ChannelSettingKey setting, boolean enabled) {
        write(tx -> this.delegate.setWithDsl(tx, key, setting, enabled));
    }

    @Override
    public void setAll(long key, Map<ChannelSettingKey, Boolean> values) {
        if (values.isEmpty()) {
            return;
        }
        write(tx -> this.delegate.setAllWithDsl(tx, key, values));
    }

    @Override
    public void setMinIntensity(long key, SeismicIntensity intensity) {
        write(tx -> this.delegate.setMinIntensityWithDsl(tx, key, intensity));
    }

    @Override
    public void setWebhook(long key, ChannelWebhook webhook) {
        write(tx -> this.delegate.setWebhookWithDsl(tx, key, webhook));
    }

    @Override
    public void setLang(long key, String lang) {
        write(tx -> this.delegate.setLangWithDsl(tx, key, lang));
    }

    @Override
    public int removeByGuildId(long guildId) {
        return write(tx -> this.delegate.removeByGuildIdWithDsl(tx, guildId));
    }

    @Override
    public int clearWebhookByUrls(Collection<String> webhookUrls) {
        if (webhookUrls.isEmpty()) {
            return 0;
        }
        return write(tx -> this.delegate.clearWebhookByUrlsWithDsl(tx, webhookUrls));
    }

    @Override
    public int setLangByGuildId(long guildId, String lang) {
        return write(tx -> this.delegate.setLangByGuildIdWithDsl(tx, guildId, lang));
    }
}
