package net.teamfruit.eewbot.slashcommand;

import discord4j.core.object.entity.channel.GuildChannel;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlashCommandUtilsTest {

    @Test
    void createDefaultChannelForTargetUsesTargetIdForNonThreadChannels() {
        Channel registered = SlashCommandUtils.createDefaultChannelForTarget(200L, 100L, "ja", false, null);

        assertThat(registered).isEqualTo(Channel.createDefault(100L, 200L, null, "ja"));
    }

    @Test
    void createDefaultChannelForTargetUsesParentIdForThreadChannels() {
        Channel registered = SlashCommandUtils.createDefaultChannelForTarget(200L, 100L, "ja", true, 300L);

        assertThat(registered).isEqualTo(Channel.createDefault(100L, 300L, 200L, "ja"));
    }

    @Test
    void createDefaultChannelForTargetThrowsWhenThreadParentIdIsMissing() {
        assertThatThrownBy(() -> SlashCommandUtils.createDefaultChannelForTarget(200L, 100L, "ja", true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Thread channel does not have a parentId");
    }

    @Test
    void createAndRegisterDefaultUsesTargetIdForNonThreadChannels() {
        RecordingRegistry registry = new RecordingRegistry();
        GuildChannel guildChannel = createGuildChannelProxy();

        Channel registered = SlashCommandUtils.createAndRegisterDefault(registry, guildChannel, 200L, 100L, "ja");

        assertThat(registered).isEqualTo(Channel.createDefault(100L, 200L, null, "ja"));
        assertThat(registry.lastKey).isEqualTo(200L);
        assertThat(registry.lastChannel).isEqualTo(registered);
    }

    private static GuildChannel createGuildChannelProxy() {
        return (GuildChannel) Proxy.newProxyInstance(
                GuildChannel.class.getClassLoader(),
                new Class<?>[]{GuildChannel.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "GuildChannelProxy";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class RecordingRegistry implements DestinationAdminRegistry {

        private final Map<Long, Channel> channels = new HashMap<>();
        private Long lastKey;
        private Channel lastChannel;

        @Override
        public Channel get(long key) {
            return this.channels.get(key);
        }

        @Override
        public boolean exists(long key) {
            return this.channels.containsKey(key);
        }

        @Override
        public void put(long key, Channel channel) {
            this.lastKey = key;
            this.lastChannel = channel;
            this.channels.put(key, channel);
        }

        @Override
        public void remove(long key) {
            this.channels.remove(key);
        }

        @Override
        public void set(long key, String name, boolean bool) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMinIntensity(long key, SeismicIntensity intensity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setWebhook(long key, ChannelWebhook webhook) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLang(long key, String lang) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> getWebhookAbsentChannels() {
            return List.of();
        }

        @Override
        public List<Long> getWebhookAbsentChannels(ChannelFilter filter) {
            return List.of();
        }

        @Override
        public int removeByGuildId(long guildId) {
            return 0;
        }

        @Override
        public int clearWebhookByUrls(Collection<String> webhookUrls) {
            return 0;
        }

        @Override
        public int setLangByGuildId(long guildId, String lang) {
            return 0;
        }

        @Override
        public Map<Long, Channel> getAllChannels() {
            return this.channels;
        }

        @Override
        public boolean isWebhookExclusiveToTarget(long webhookId, long targetId) {
            return false;
        }
    }
}
