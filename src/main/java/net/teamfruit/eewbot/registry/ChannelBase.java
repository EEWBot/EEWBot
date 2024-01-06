package net.teamfruit.eewbot.registry;

import reactor.util.annotation.Nullable;

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
    public String toString() {
        return "ChannelBase{" +
                "webhook=" + this.webhook +
                ", lang='" + this.lang + '\'' +
                '}';
    }
}
