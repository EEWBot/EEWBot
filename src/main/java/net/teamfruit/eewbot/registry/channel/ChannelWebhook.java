package net.teamfruit.eewbot.registry.channel;

import java.util.Objects;

public class ChannelWebhook {

    private long id;
    private String token;

    public ChannelWebhook(long id, String token) {
        this.id = id;
        this.token = token;
    }

    public long getId() {
        return this.id;
    }

    void setId(long id) {
        this.id = id;
    }

    public String getToken() {
        return this.token;
    }

    void setToken(String token) {
        this.token = token;
    }

    public String getPath() {
        return "/" + this.id + "/" + this.token;
    }

    public String getUrl() {
        return "https://discord.com/api/webhooks/" + this.id + "/" + this.token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelWebhook webhook = (ChannelWebhook) o;
        return Objects.equals(this.id, webhook.id) && Objects.equals(this.token, webhook.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.token);
    }

    @Override
    public String toString() {
        return "Webhook{" +
                "id=" + this.id +
                ", token='" + this.token + '\'' +
                '}';
    }
}
