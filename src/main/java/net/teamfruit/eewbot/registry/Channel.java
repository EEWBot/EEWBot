package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.i18n.I18n;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
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

    //    @CommandName("強震モニタ")
    public boolean monitor = true;

    public SeismicIntensity minIntensity = SeismicIntensity.ONE;

    public Webhook webhook;

    public String lang = I18n.DEFAULT_LANGUAGE;

    public Channel() {
    }

    public Channel(final boolean eewAlert, final boolean eewPrediction, final boolean eewDecimation, final boolean quakeInfo, final boolean quakeInfoDetail, final boolean monitor, final SeismicIntensity minIntensity, Webhook webhook) {
        this.eewAlert = eewAlert;
        this.eewPrediction = eewPrediction;
        this.eewDecimation = eewDecimation;
        this.quakeInfo = quakeInfo;
        this.monitor = monitor;
        this.minIntensity = minIntensity;
        this.webhook = webhook;
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
                .findAny().orElseThrow(IllegalArgumentException::new);
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
                .anyMatch(field -> field.isAnnotationPresent(CommandName.class) && (field.getAnnotation(CommandName.class).value().equals(name) || field.getName().equals(name)));

    }

    public Map<String, Boolean> getCommandFields() {
        return Arrays.stream(getClass().getFields())
                .filter(field -> field.isAnnotationPresent(CommandName.class))
                .collect(Collectors.toMap(Field::getName, field -> {
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
                SeismicIntensity.ONE,
                null);
    }

    public static class Webhook {

        public String id;
        public String token;
        public String threadId;

        public Webhook(String id, String token, String threadId) {
            this.id = id;
            this.token = token;
            this.threadId = threadId;
        }

        public Webhook(String id, String token) {
            this(id, token, null);
        }

        public String getJoined() {
            if (this.threadId != null) {
                return this.id + "/" + this.token + "?thread_id=" + this.threadId;
            } else {
                return this.id + "/" + this.token;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Webhook webhook = (Webhook) o;
            return Objects.equals(id, webhook.id) && Objects.equals(token, webhook.token) && Objects.equals(threadId, webhook.threadId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, token, threadId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return eewAlert == channel.eewAlert && eewPrediction == channel.eewPrediction && eewDecimation == channel.eewDecimation && quakeInfo == channel.quakeInfo && monitor == channel.monitor && minIntensity == channel.minIntensity && Objects.equals(webhook, channel.webhook) && Objects.equals(lang, channel.lang);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eewAlert, eewPrediction, eewDecimation, quakeInfo, monitor, minIntensity, webhook, lang);
    }
}
