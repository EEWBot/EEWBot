package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.i18n.I18n;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Channel {

    @CommandName("EEW警報")
    public boolean eewAlert = true;

    @CommandName("EEW予報")
    public boolean eewPrediction = true;

    @CommandName("EEW間引き")
    public boolean eewDecimation = true;

    @CommandName("地震情報")
    public boolean quakeInfo = true;

    @CommandName("強震モニタ")
    public boolean monitor = true;

    public SeismicIntensity minIntensity = SeismicIntensity.ONE;

    public String lang = I18n.DEFAULT_LANGUAGE;

    public Channel() {
    }

    public Channel(final boolean eewAlert, final boolean eewPrediction, final boolean eewDecimation, final boolean quakeInfo, final boolean quakeInfoDetail, final boolean monitor, final SeismicIntensity minIntensity) {
        this.eewAlert = eewAlert;
        this.eewPrediction = eewPrediction;
        this.eewDecimation = eewDecimation;
        this.quakeInfo = quakeInfo;
        this.monitor = monitor;
        this.minIntensity = minIntensity;
    }

    /*
     * TODO: minIntensity
     */
    public boolean value(final String name) {
        return Arrays.stream(getClass().getFields())
                .filter(field -> field.isAnnotationPresent(CommandName.class) && (field.getAnnotation(CommandName.class).value().equals(name) || field.getName().equals(name)))
                .map(field -> {
                    try {
                        return field.getBoolean(this);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findAny().orElseThrow(() -> new IllegalArgumentException());
    }

    public void set(final String name, final boolean bool) {
        Arrays.stream(getClass().getFields())
                .filter(field -> field.isAnnotationPresent(CommandName.class) && (field.getAnnotation(CommandName.class).value().equals(name) || field.getName().equals(name)))
                .findAny().ifPresent(field -> {
                    try {
                        field.setBoolean(this, bool);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public boolean exits(final String name) {
        return Arrays.stream(getClass().getFields())
                .filter(field -> field.isAnnotationPresent(CommandName.class) && (field.getAnnotation(CommandName.class).value().equals(name) || field.getName().equals(name)))
                .count() > 0;

    }

    public Map<String, Boolean> getCommandFields() {
        return Arrays.stream(getClass().getFields())
                .filter(field -> field.isAnnotationPresent(CommandName.class))
                .collect(Collectors.toMap(field -> field.getName(), field -> {
                    try {
                        return field.getBoolean(this);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    @Override
    public String toString() {
        return Arrays.stream(getClass().getFields())
                .filter(field -> field.isAnnotationPresent(CommandName.class))
                .map(field -> {
                    try {
                        return String.format("`%s` %s", field.getAnnotation(CommandName.class).value(), field.getBoolean(this));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                // TODO
                .collect(Collectors.joining("\n")) + "\n`最小通知震度` " + this.minIntensity.getSimple();
    }

    public static Optional<String> toCommandName(String fieldName) {
        return Arrays.stream(Channel.class.getFields())
                .filter(field -> field.getName().equals(fieldName) && field.isAnnotationPresent(CommandName.class))
                .map(field -> field.getAnnotation(CommandName.class).value())
                .findAny();
    }

    @SuppressWarnings("deprecation")
    public static Channel fromOldChannel(final OldChannel old) {
        return new Channel(old.eewAlert.get(),
                old.eewPrediction.get(),
                old.eewDecimation.get(),
                old.quakeInfo.get(),
                old.quakeInfoDetail.get(),
                old.monitor.get(),
                SeismicIntensity.ONE);
    }
}
