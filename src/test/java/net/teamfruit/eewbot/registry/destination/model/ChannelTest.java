package net.teamfruit.eewbot.registry.destination.model;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelTest {

    @Test
    void defaultChannelAllowsAllIntensities() {
        Channel channel = Channel.createDefault(100L, 1L, null, "ja_jp");

        assertThat(channel.getMinIntensity()).isEqualTo(SeismicIntensity.UNKNOWN);
        for (SeismicIntensity intensity : SeismicIntensity.values()) {
            assertThat(ChannelFilter.builder().intensity(intensity).build().test(channel)).isTrue();
        }
    }
}
