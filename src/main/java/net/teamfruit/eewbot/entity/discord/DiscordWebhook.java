package net.teamfruit.eewbot.entity.discord;

import java.util.ArrayList;
import java.util.List;

public class DiscordWebhook {

    public String content;
    public String username;
    public String avatar_url;
    public List<DiscordEmbed> embeds;
    public String thread_name;

    public static DiscordWebhook.Builder builder() {
        return new DiscordWebhook.Builder();
    }

    public static class Builder {

        private final DiscordWebhook webhook;

        public Builder() {
            this.webhook = new DiscordWebhook();
        }

        public Builder content(String content) {
            this.webhook.content = content;
            return this;
        }

        public Builder username(String username) {
            this.webhook.username = username;
            return this;
        }

        public Builder avatar_url(String avatar_url) {
            this.webhook.avatar_url = avatar_url;
            return this;
        }

        public Builder embeds(List<DiscordEmbed> embeds) {
            this.webhook.embeds = embeds;
            return this;
        }

        public Builder addEmbed(DiscordEmbed embed) {
            if (this.webhook.embeds == null)
                this.webhook.embeds = new ArrayList<>();
            this.webhook.embeds.add(embed);
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
