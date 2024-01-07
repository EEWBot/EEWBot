package net.teamfruit.eewbot.registry;

import reactor.util.annotation.Nullable;

import java.util.Objects;

public class ChannelBase {

    protected Webhook webhook;
    protected String lang;

    public ChannelBase(Webhook webhook, String lang) {
        this.webhook = webhook;
        this.lang = lang;
    }

    public @Nullable Webhook getWebhook() {
        return this.webhook;
    }

    void setWebhook(Webhook webhook) {
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

        if (!Objects.equals(this.webhook, that.webhook)) return false;
        return Objects.equals(this.lang, that.lang);
    }

    @Override
    public int hashCode() {
        int result = this.webhook != null ? this.webhook.hashCode() : 0;
        result = 31 * result + (this.lang != null ? this.lang.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ChannelBase{" +
                "webhook=" + this.webhook +
                ", lang='" + this.lang + '\'' +
                '}';
    }
}
