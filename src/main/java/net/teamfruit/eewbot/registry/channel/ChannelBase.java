package net.teamfruit.eewbot.registry.channel;

import reactor.util.annotation.Nullable;

import java.util.Objects;

public class ChannelBase {

    protected Boolean isGuild;
    protected Long guildId;
    protected ChannelWebhook webhook;
    protected String lang;

    public ChannelBase(boolean isGuild, Long guildId, ChannelWebhook webhook, String lang) {
        this.isGuild = isGuild;
        this.guildId = guildId;
        this.webhook = webhook;
        this.lang = lang;
    }

    public ChannelBase(ChannelWebhook webhook, String lang) {
        this.webhook = webhook;
        this.lang = lang;
    }

    public @Nullable Boolean isGuild() {
        return this.isGuild;
    }

    void setGuild(boolean guild) {
        this.isGuild = guild;
    }

    public @Nullable Long getGuildId() {
        return this.guildId;
    }

    void setGuildId(long guildId) {
        this.guildId = guildId;
    }

    public @Nullable ChannelWebhook getWebhook() {
        return this.webhook;
    }

    void setWebhook(ChannelWebhook webhook) {
        this.webhook = webhook;
    }

    public String getLang() {
        return this.lang;
    }

    void setLang(String lang) {
        this.lang = lang;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChannelBase that = (ChannelBase) o;

        if (!Objects.equals(this.isGuild, that.isGuild)) return false;
        if (!Objects.equals(this.guildId, that.guildId)) return false;
        if (!Objects.equals(this.webhook, that.webhook)) return false;
        return this.lang.equals(that.lang);
    }

    @Override
    public int hashCode() {
        int result = this.isGuild != null ? this.isGuild.hashCode() : 0;
        result = 31 * result + (this.guildId != null ? this.guildId.hashCode() : 0);
        result = 31 * result + (this.webhook != null ? this.webhook.hashCode() : 0);
        result = 31 * result + this.lang.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ChannelBase{" +
                "isGuild=" + this.isGuild +
                ", guildId=" + this.guildId +
                ", webhook=" + this.webhook +
                ", lang='" + this.lang + '\'' +
                '}';
    }

}
