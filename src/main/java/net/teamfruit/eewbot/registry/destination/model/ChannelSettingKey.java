package net.teamfruit.eewbot.registry.destination.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The on/off notification settings of a destination.
 * <p>
 * This is the single definition every layer derives from: the select-menu option value
 * ({@link #id()}), the i18n keys and the storage column (mapped exhaustively by the SQL layer),
 * so adding a setting without handling it everywhere fails at compile time.
 */
public enum ChannelSettingKey {
    EEW_ALERT("eewAlert", ChannelSettingType.BASE, Channel::isEewAlert),
    EEW_PREDICTION("eewPrediction", ChannelSettingType.BASE, Channel::isEewPrediction),
    QUAKE_INFO("quakeInfo", ChannelSettingType.BASE, Channel::isQuakeInfo),
    TSUNAMI("tsunami", ChannelSettingType.BASE, Channel::isTsunami),
    EEW_DECIMATION("eewDecimation", ChannelSettingType.MODIFIER, Channel::isEewDecimation);

    private final String id;
    private final ChannelSettingType type;
    private final Predicate<Channel> reader;

    ChannelSettingKey(String id, ChannelSettingType type, Predicate<Channel> reader) {
        this.id = id;
        this.type = type;
        this.reader = reader;
    }

    /** Stable external identifier, used as the Discord select-menu option value. */
    public String id() {
        return this.id;
    }

    public ChannelSettingType type() {
        return this.type;
    }

    public boolean isEnabled(Channel channel) {
        return this.reader.test(channel);
    }

    public String labelKey() {
        return i18nPrefix() + ".label";
    }

    public String descriptionKey() {
        return i18nPrefix() + ".desc";
    }

    private String i18nPrefix() {
        return "eewbot.scmd.setup." + this.type.getCustomId() + "." + this.id.toLowerCase(Locale.ROOT);
    }

    public static Optional<ChannelSettingKey> fromId(String id) {
        return Arrays.stream(values()).filter(key -> key.id.equals(id)).findAny();
    }

    public static List<ChannelSettingKey> ofType(ChannelSettingType type) {
        return Arrays.stream(values()).filter(key -> key.type == type).toList();
    }
}
