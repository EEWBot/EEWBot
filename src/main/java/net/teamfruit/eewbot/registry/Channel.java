package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.i18n.I18n;
import reactor.util.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class Channel {

    public static final List<String> COMMAND_KEYS;

    static {
        COMMAND_KEYS = Arrays.stream(Channel.class.getFields())
                .filter(field -> field.isAnnotationPresent(CommandName.class))
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableList());
    }

    @CommandName("EEW警報")
    private boolean eewAlert = true;

    @CommandName("EEW予報")
    private boolean eewPrediction = true;

    @CommandName("EEW間引き")
    private boolean eewDecimation = true;

    @CommandName("地震情報")
    private boolean quakeInfo = true;

    //    @CommandName("強震モニタ")
//    public boolean monitor = true;

    private SeismicIntensity minIntensity = SeismicIntensity.ONE;

    private Webhook webhook;

    private String lang = I18n.DEFAULT_LANGUAGE;

    public Channel() {
    }

    public Channel(final boolean eewAlert, final boolean eewPrediction, final boolean eewDecimation, final boolean quakeInfo, final SeismicIntensity minIntensity, Webhook webhook) {
        this.eewAlert = eewAlert;
        this.eewPrediction = eewPrediction;
        this.eewDecimation = eewDecimation;
        this.quakeInfo = quakeInfo;
        this.minIntensity = minIntensity;
        this.webhook = webhook;
    }

    public boolean isEewAlert() {
        return eewAlert;
    }

    void setEewAlert(boolean eewAlert) {
        this.eewAlert = eewAlert;
    }

    public boolean isEewPrediction() {
        return eewPrediction;
    }

    void setEewPrediction(boolean eewPrediction) {
        this.eewPrediction = eewPrediction;
    }

    public boolean isEewDecimation() {
        return eewDecimation;
    }

    void setEewDecimation(boolean eewDecimation) {
        this.eewDecimation = eewDecimation;
    }

    public boolean isQuakeInfo() {
        return quakeInfo;
    }

    void setQuakeInfo(boolean quakeInfo) {
        this.quakeInfo = quakeInfo;
    }

    public SeismicIntensity getMinIntensity() {
        return minIntensity;
    }

    void setMinIntensity(SeismicIntensity minIntensity) {
        this.minIntensity = minIntensity;
    }

    public @Nullable Webhook getWebhook() {
        return webhook;
    }

    void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public String getLang() {
        return lang;
    }

    void setLang(String lang) {
        this.lang = lang;
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

    void set(final String name, final boolean bool) {
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
        Map<String, Boolean> map = new HashMap<>();
        map.put("eewAlert", eewAlert);
        map.put("eewPrediction", eewPrediction);
        map.put("eewDecimation", eewDecimation);
        map.put("quakeInfo", quakeInfo);
        return map;
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

    public static class Webhook {

        private String id;
        private String token;
        private String threadId;

        public Webhook(String id, String token, String threadId) {
            this.id = id;
            this.token = token;
            this.threadId = threadId;
        }

        public Webhook(String id, String token) {
            this(id, token, null);
        }

        public String getId() {
            return id;
        }

        void setId(String id) {
            this.id = id;
        }

        public String getToken() {
            return token;
        }

        void setToken(String token) {
            this.token = token;
        }

        public @Nullable String getThreadId() {
            return threadId;
        }

        void setThreadId(String threadId) {
            this.threadId = threadId;
        }

        public String getJoined() {
            if (this.threadId != null) {
                return "/" + this.id + "/" + this.token + "?thread_id=" + this.threadId;
            } else {
                return "/" + this.id + "/" + this.token;
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
        return eewAlert == channel.eewAlert && eewPrediction == channel.eewPrediction && eewDecimation == channel.eewDecimation && quakeInfo == channel.quakeInfo && minIntensity == channel.minIntensity && Objects.equals(webhook, channel.webhook) && Objects.equals(lang, channel.lang);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eewAlert, eewPrediction, eewDecimation, quakeInfo, minIntensity, webhook, lang);
    }
}
