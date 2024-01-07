package net.teamfruit.eewbot.registry;

import reactor.util.annotation.Nullable;

import java.util.Objects;

public class Webhook {

    private long id;
    private String token;
    private Long threadId;

    public Webhook(long id, String token, Long threadId) {
        this.id = id;
        this.token = token;
        this.threadId = threadId;
    }

    public Webhook(long id, String token) {
        this(id, token, null);
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

    public @Nullable Long getThreadId() {
        return this.threadId;
    }

    void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public String getPath() {
        if (this.threadId != null) {
            return "/" + this.id + "/" + this.token + "?thread_id=" + this.threadId;
        } else {
            return "/" + this.id + "/" + this.token;
        }
    }

    public String getUrl() {
        if (this.threadId != null) {
            return "https://discord.com/api/webhooks/" + this.id + "/" + this.token + "?thread_id=" + this.threadId;
        } else {
            return "https://discord.com/api/webhooks/" + this.id + "/" + this.token;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Webhook webhook = (Webhook) o;
        return Objects.equals(this.id, webhook.id) && Objects.equals(this.token, webhook.token) && Objects.equals(this.threadId, webhook.threadId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.token, this.threadId);
    }

    @Override
    public String toString() {
        return "Webhook{" +
                "id=" + this.id +
                ", token='" + this.token + '\'' +
                ", threadId=" + this.threadId +
                '}';
    }
}
