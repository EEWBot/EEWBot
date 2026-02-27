package net.teamfruit.eewbot.slashcommand;

import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.discordjson.json.ChannelData;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlashCommandUtilsTest {

    private static final Unsafe UNSAFE = lookupUnsafe();
    private static final Class<?> BASE_CHANNEL_CLASS = loadBaseChannelClass();

    @Test
    void createAndRegisterDefaultUsesTargetIdForNonThreadChannels() {
        RecordingRegistry registry = new RecordingRegistry();
        GuildChannel guildChannel = createGuildChannelProxy();

        Channel registered = SlashCommandUtils.createAndRegisterDefault(registry, guildChannel, 200L, 100L, "ja");

        assertThat(registered).isEqualTo(Channel.createDefault(100L, 200L, null, "ja"));
        assertThat(registry.lastKey).isEqualTo(200L);
        assertThat(registry.lastChannel).isEqualTo(registered);
    }

    @Test
    void createAndRegisterDefaultUsesParentIdForThreadChannels() throws InstantiationException, NoSuchFieldException {
        RecordingRegistry registry = new RecordingRegistry();
        ThreadChannel threadChannel = createThreadChannel(200L, 300L);

        Channel registered = SlashCommandUtils.createAndRegisterDefault(registry, threadChannel, 200L, 100L, "ja");

        assertThat(registered).isEqualTo(Channel.createDefault(100L, 300L, 200L, "ja"));
        assertThat(registry.lastKey).isEqualTo(200L);
        assertThat(registry.lastChannel).isEqualTo(registered);
    }

    @Test
    void createAndRegisterDefaultThrowsWhenThreadParentIdIsMissing() throws InstantiationException, NoSuchFieldException {
        RecordingRegistry registry = new RecordingRegistry();
        ThreadChannel threadChannel = createThreadChannel(200L, null);

        assertThatThrownBy(() -> SlashCommandUtils.createAndRegisterDefault(registry, threadChannel, 200L, 100L, "ja"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Thread channel does not have a parentId");
        assertThat(registry.lastKey).isNull();
        assertThat(registry.getAllChannels()).isEmpty();
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

    private static ThreadChannel createThreadChannel(long threadId, Long parentId) throws InstantiationException, NoSuchFieldException {
        ThreadChannel threadChannel = (ThreadChannel) UNSAFE.allocateInstance(ThreadChannel.class);
        ChannelData data = buildThreadChannelData(threadId, parentId);
        Field dataField = BASE_CHANNEL_CLASS.getDeclaredField("data");
        dataField.setAccessible(true);
        try {
            dataField.set(threadChannel, data);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to set ThreadChannel data", e);
        }
        return threadChannel;
    }

    private static ChannelData buildThreadChannelData(long threadId, Long parentId) {
        var builder = ChannelData.builder()
                .id(threadId)
                .type(11);
        if (parentId != null) {
            builder.parentId(parentId);
        }
        return builder.build();
    }

    private static Unsafe lookupUnsafe() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access Unsafe", e);
        }
    }

    private static Class<?> loadBaseChannelClass() {
        try {
            return Class.forName("discord4j.core.object.entity.channel.BaseChannel");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load BaseChannel class", e);
        }
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
        public boolean isWebhookForThread(long webhookId, long targetId) {
            return false;
        }
    }
}
