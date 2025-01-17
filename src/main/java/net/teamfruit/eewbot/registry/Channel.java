package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.i18n.I18nKey;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
public class Channel extends ChannelBase {

    public static final List<String> COMMAND_KEYS;

    static {
        COMMAND_KEYS = Arrays.stream(Channel.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ChannelSetting.class))
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableList());
    }

    @ChannelSetting(ChannelSettingType.BASE)
    @I18nKey("eewbot.scmd.setup.base.eewalert.label")
    private boolean eewAlert;

    @ChannelSetting(ChannelSettingType.BASE)
    @I18nKey("eewbot.scmd.setup.base.eewprediction.label")
    private boolean eewPrediction;

    @ChannelSetting(ChannelSettingType.BASE)
    @I18nKey("eewbot.scmd.setup.base.quakeinfo.label")
    private boolean quakeInfo;

    @ChannelSetting(ChannelSettingType.MODIFIER)
    @I18nKey("eewbot.scmd.setup.modifier.eewdecimation.label")
    private boolean eewDecimation;

    private SeismicIntensity minIntensity;

    public Channel(final boolean isGuild, final Long guildId, final boolean eewAlert, final boolean eewPrediction, final boolean eewDecimation, final boolean quakeInfo, final SeismicIntensity minIntensity, Webhook webhook, String lang) {
        super(isGuild, guildId, webhook, lang);
        this.eewAlert = eewAlert;
        this.eewPrediction = eewPrediction;
        this.eewDecimation = eewDecimation;
        this.quakeInfo = quakeInfo;
        this.minIntensity = minIntensity;
    }

    public static Channel createDefault(Long guildId, String lang) {
        return new Channel(guildId != null, guildId, false, false, false, false, SeismicIntensity.ONE, null, lang);
    }

    public boolean isEewAlert() {
        return this.eewAlert;
    }

    public boolean isEewPrediction() {
        return this.eewPrediction;
    }

    public boolean isEewDecimation() {
        return this.eewDecimation;
    }

    public boolean isQuakeInfo() {
        return this.quakeInfo;
    }

    public SeismicIntensity getMinIntensity() {
        return this.minIntensity;
    }

    void setMinIntensity(SeismicIntensity minIntensity) {
        this.minIntensity = minIntensity;
    }

    /*
     * TODO: minIntensity
     */
    public boolean value(final String name) {
        return Arrays.stream(getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ChannelSetting.class) && field.getName().equals(name))
                .map(field -> {
                    try {
                        return field.getBoolean(this);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findAny().orElseThrow(IllegalArgumentException::new);
    }

    void set(final String name, final boolean bool) {
        Arrays.stream(getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ChannelSetting.class) && field.getName().equals(name))
                .findAny().ifPresent(field -> {
                    try {
                        field.setBoolean(this, bool);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public Map<String, Boolean> getSettingsByType(ChannelSettingType type) {
        return Arrays.stream(getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ChannelSetting.class) && field.getAnnotation(ChannelSetting.class).value() == type)
                .collect(Collectors.toMap(Field::getName, field -> {
                    try {
                        return field.getBoolean(this);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    public static Optional<String> toI18nKey(String fieldName) {
        return Arrays.stream(Channel.class.getDeclaredFields())
                .filter(field -> field.getName().equals(fieldName) && field.isAnnotationPresent(I18nKey.class))
                .map(field -> field.getAnnotation(I18nKey.class).value())
                .findAny();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return this.eewAlert == channel.eewAlert && this.eewPrediction == channel.eewPrediction && this.eewDecimation == channel.eewDecimation && this.quakeInfo == channel.quakeInfo && this.minIntensity == channel.minIntensity && Objects.equals(this.webhook, channel.webhook) && Objects.equals(this.lang, channel.lang);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.eewAlert, this.eewPrediction, this.eewDecimation, this.quakeInfo, this.minIntensity, this.webhook, this.lang);
    }

    @Override
    public String toString() {
        return "Channel{" +
                "eewAlert=" + this.eewAlert +
                ", eewPrediction=" + this.eewPrediction +
                ", eewDecimation=" + this.eewDecimation +
                ", quakeInfo=" + this.quakeInfo +
                ", minIntensity=" + this.minIntensity +
                '}';
    }

}
