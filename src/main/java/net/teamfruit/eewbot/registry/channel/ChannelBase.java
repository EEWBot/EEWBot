package net.teamfruit.eewbot.registry.channel;

import reactor.util.annotation.Nullable;

import java.util.Objects;

public class ChannelBase {

    protected Long guildId;
    protected Long channelId;
    protected Long threadId;
    protected ChannelWebhook webhook;
    protected String lang;

    public ChannelBase(Long guildId, Long channelId, Long threadId, ChannelWebhook webhook, String lang) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.threadId = threadId;
        this.webhook = webhook;
        this.lang = lang;
    }

    public boolean isGuild() {
        return this.guildId != null;
    }

    public @Nullable Long getGuildId() {
        return this.guildId;
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

    public @Nullable Long getChannelId() {
        return this.channelId;
    }

    void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public @Nullable Long getThreadId() {
        return this.threadId;
    }

    void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public @Nullable String getWebhookUrl() {
        if (this.webhook == null) return null;
        return this.webhook.getUrl();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChannelBase that = (ChannelBase) o;

        if (!Objects.equals(this.guildId, that.guildId)) return false;
        if (!Objects.equals(this.channelId, that.channelId)) return false;
        if (!Objects.equals(this.threadId, that.threadId)) return false;
        if (!Objects.equals(this.webhook, that.webhook)) return false;
        return Objects.equals(this.lang, that.lang);
    }

    @Override
    public int hashCode() {
        int result = this.guildId != null ? this.guildId.hashCode() : 0;
        result = 31 * result + (this.channelId != null ? this.channelId.hashCode() : 0);
        result = 31 * result + (this.threadId != null ? this.threadId.hashCode() : 0);
        result = 31 * result + (this.webhook != null ? this.webhook.hashCode() : 0);
        result = 31 * result + (this.lang != null ? this.lang.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ChannelBase{" +
                "guildId=" + this.guildId +
                ", channelId=" + this.channelId +
                ", threadId=" + this.threadId +
                ", webhook=" + this.webhook +
                ", lang='" + this.lang + '\'' +
                '}';
    }

}
