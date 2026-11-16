package net.teamfruit.eewbot.registry.destination.model;

import net.teamfruit.eewbot.i18n.I18n;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSettingKeyTest {

    @Test
    void idsAreUniqueAndResolveBack() {
        assertThat(Arrays.stream(ChannelSettingKey.values()).map(ChannelSettingKey::id)).doesNotHaveDuplicates();
        for (ChannelSettingKey setting : ChannelSettingKey.values())
            assertThat(ChannelSettingKey.fromId(setting.id())).contains(setting);
        assertThat(ChannelSettingKey.fromId("unknown")).isEmpty();
    }

    @Test
    void everySettingBelongsToExactlyOneMenu() {
        assertThat(Arrays.stream(ChannelSettingType.values())
                .flatMap(type -> ChannelSettingKey.ofType(type).stream()))
                .containsExactlyInAnyOrder(ChannelSettingKey.values());
    }

    @Test
    void derivedLabelAndDescriptionKeysExistInTheFallbackLanguage() {
        // Other languages fall back to ja_jp, so this is the lookup that must never miss.
        I18n i18n = new I18n("ja_jp");
        for (ChannelSettingKey setting : ChannelSettingKey.values()) {
            // A missing key is echoed back unchanged.
            assertThat(i18n.get("ja_jp", setting.labelKey())).as(setting.labelKey())
                    .isNotBlank().isNotEqualTo(setting.labelKey());
            assertThat(i18n.get("ja_jp", setting.descriptionKey())).as(setting.descriptionKey())
                    .isNotBlank().isNotEqualTo(setting.descriptionKey());
        }
    }

    @Test
    void readsTheMatchingChannelFlag() {
        Channel channel = new Channel(1L, 2L, null, true, false, true, false, true, null, null, "ja_jp");
        assertThat(ChannelSettingKey.EEW_ALERT.isEnabled(channel)).isTrue();
        assertThat(ChannelSettingKey.EEW_PREDICTION.isEnabled(channel)).isFalse();
        assertThat(ChannelSettingKey.EEW_DECIMATION.isEnabled(channel)).isTrue();
        assertThat(ChannelSettingKey.QUAKE_INFO.isEnabled(channel)).isFalse();
        assertThat(ChannelSettingKey.TSUNAMI.isEnabled(channel)).isTrue();
    }
}
