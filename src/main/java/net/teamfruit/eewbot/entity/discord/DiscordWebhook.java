package net.teamfruit.eewbot.entity.discord;

import java.util.List;

public class DiscordWebhook {

    public String username;
    public String avatar_url;
    public List<DiscordComponent> components;
    public int flags = ComponentLimits.IS_COMPONENTS_V2;
    public String thread_name;

    public static DiscordWebhook.Builder builder() {
        return new DiscordWebhook.Builder();
    }

    public static class Builder {

        private final DiscordWebhook webhook;

        public Builder() {
            this.webhook = new DiscordWebhook();
        }

        public Builder username(String username) {
            this.webhook.username = username;
            return this;
        }

        public Builder avatar_url(String avatar_url) {
            this.webhook.avatar_url = avatar_url;
            return this;
        }

        public Builder components(List<DiscordComponent> components) {
            this.webhook.components = List.copyOf(components);
            return this;
        }

        public Builder thread_name(String thread_name) {
            this.webhook.thread_name = thread_name;
            return this;
        }

        public DiscordWebhook build() {
            return this.webhook;
        }
    }

}
