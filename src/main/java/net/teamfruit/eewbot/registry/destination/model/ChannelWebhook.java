package net.teamfruit.eewbot.registry.destination.model;

import org.jetbrains.annotations.NotNull;

public record ChannelWebhook(String url) {

    private static final String URL_PREFIX = "https://discord.com/api/webhooks/";

    /**
     * Extract webhook_id from URL.
     * URL format: https://discord.com/api/webhooks/{id}/{token}[?thread_id={threadId}]
     */
    public long id() {
        String path = this.url.substring(URL_PREFIX.length());
        int slashIndex = path.indexOf('/');
        return Long.parseLong(path.substring(0, slashIndex));
    }

    /**
     * Extract token from URL (removes ?thread_id= query parameter if present).
     */
    public String token() {
        String path = this.url.substring(URL_PREFIX.length());
        int slashIndex = path.indexOf('/');
        String tokenPart = path.substring(slashIndex + 1);
        int queryIndex = tokenPart.indexOf('?');
        return queryIndex >= 0 ? tokenPart.substring(0, queryIndex) : tokenPart;
    }

    /**
     * Get the full webhook URL.
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * Mask the token portion of a webhook URL for safe logging.
     * Returns the URL with the token replaced by "***".
     */
    public static String maskWebhookUrl(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return "***";
        }
        String path = url.substring(URL_PREFIX.length());
        int slashIndex = path.indexOf('/');
        if (slashIndex < 0) {
            return URL_PREFIX + path;
        }
        return URL_PREFIX + path.substring(0, slashIndex) + "/***";
    }

    /**
     * Create ChannelWebhook from id and token (without thread_id).
     */
    public static ChannelWebhook of(long id, String token) {
        return new ChannelWebhook(URL_PREFIX + id + "/" + token);
    }

    /**
     * Create ChannelWebhook from id, token, and optional threadId.
     * If threadId is not null, appends ?thread_id= to the URL.
     */
    public static ChannelWebhook of(long id, String token, Long threadId) {
        String url = URL_PREFIX + id + "/" + token;
        if (threadId != null) {
            url += "?thread_id=" + threadId;
        }
        return new ChannelWebhook(url);
    }

    @NotNull
    @Override
    public String toString() {
        return "Webhook{" +
                "url='" + this.url + '\'' +
                '}';
    }
}
