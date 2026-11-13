package net.teamfruit.eewbot.registry.destination.model;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;

import java.util.Objects;

public class ChannelFilter {

    private Boolean hasGuild;
    private boolean hasGuildPresent;
    private long guildId;
    private boolean guildIdPresent;
    private long channelId;
    private boolean channelIdPresent;
    private Long threadId;
    private boolean threadIdPresent;
    private Boolean isThread;
    private boolean isThreadPresent;
    private boolean eewAlert;
    private boolean eewAlertPresent;
    private boolean eewPrediction;
    private boolean eewPredictionPresent;
    private boolean eewDecimation;
    private boolean eewDecimationPresent;
    private boolean quakeInfo;
    private boolean quakeInfoPresent;
    private boolean tsunami;
    private boolean tsunamiPresent;
    private SeismicIntensity intensity;
    private boolean intensityPresent;
    private long webhookId;
    private boolean webhookIdPresent;

    public boolean isHasGuildPresent() {
        return this.hasGuildPresent;
    }

    public boolean isGuildIdPresent() {
        return this.guildIdPresent;
    }

    public boolean isChannelIdPresent() {
        return this.channelIdPresent;
    }

    public boolean isThreadIdPresent() {
        return this.threadIdPresent;
    }

    public boolean isIsThreadPresent() {
        return this.isThreadPresent;
    }

    public boolean isEewAlertPresent() {
        return this.eewAlertPresent;
    }

    public boolean isEewPredictionPresent() {
        return this.eewPredictionPresent;
    }

    public boolean isEewDecimationPresent() {
        return this.eewDecimationPresent;
    }

    public boolean isQuakeInfoPresent() {
        return this.quakeInfoPresent;
    }

    public boolean isTsunamiPresent() {
        return this.tsunamiPresent;
    }

    public boolean isIntensityPresent() {
        return this.intensityPresent;
    }

    public boolean isWebhookIdPresent() {
        return this.webhookIdPresent;
    }

    public Boolean getHasGuild() {
        return this.hasGuild;
    }

    public long getGuildId() {
        return this.guildId;
    }

    public long getChannelId() {
        return this.channelId;
    }

    public Long getThreadId() {
        return this.threadId;
    }

    public Boolean getIsThread() {
        return this.isThread;
    }

    public boolean getEewAlert() {
        return this.eewAlert;
    }

    public boolean getEewPrediction() {
        return this.eewPrediction;
    }

    public boolean getEewDecimation() {
        return this.eewDecimation;
    }

    public boolean getQuakeInfo() {
        return this.quakeInfo;
    }

    public boolean getTsunami() {
        return this.tsunami;
    }

    public SeismicIntensity getIntensity() {
        return this.intensity;
    }

    public long getWebhookId() {
        return this.webhookId;
    }

    public boolean test(Channel channel) {
        if (this.hasGuildPresent && this.hasGuild != null && channel.isGuild() != this.hasGuild)
            return false;
        if (this.guildIdPresent && !Objects.equals(channel.getGuildId(), this.guildId))
            return false;
        if (this.channelIdPresent && !Objects.equals(channel.getChannelId(), this.channelId))
            return false;
        if (this.threadIdPresent && !Objects.equals(channel.getThreadId(), this.threadId))
            return false;
        if (this.isThreadPresent) {
            boolean channelIsThread = channel.getThreadId() != null;
            if (this.isThread != null && this.isThread != channelIsThread)
                return false;
        }
        if (this.eewAlertPresent && channel.isEewAlert() != this.eewAlert)
            return false;
        if (this.eewPredictionPresent && channel.isEewPrediction() != this.eewPrediction)
            return false;
        if (this.eewDecimationPresent && channel.isEewDecimation() != this.eewDecimation)
            return false;
        if (this.quakeInfoPresent && channel.isQuakeInfo() != this.quakeInfo)
            return false;
        if (this.tsunamiPresent && channel.isTsunami() != this.tsunami)
            return false;
        if (this.intensityPresent && channel.getMinIntensity().getCode() > this.intensity.getCode())
            return false;
        if (this.webhookIdPresent && channel.getWebhook() == null)
            return false;
        return !this.webhookIdPresent || channel.getWebhook().id() == this.webhookId;
    }

    public boolean test(DeliverySnapshot.DeliveryChannel channel) {
        if (this.hasGuildPresent && this.hasGuild != null && channel.isGuild() != this.hasGuild)
            return false;
        if (this.guildIdPresent && (channel.guildId() == null || channel.guildId() != this.guildId))
            return false;
        if (this.channelIdPresent && channel.channelId() != this.channelId)
            return false;
        if (this.threadIdPresent) {
            if (this.threadId == null) {
                if (channel.threadId() != null) return false;
            } else if (!this.threadId.equals(channel.threadId())) {
                return false;
            }
        }
        if (this.isThreadPresent) {
            boolean channelIsThread = channel.threadId() != null;
            if (this.isThread != null && this.isThread != channelIsThread)
                return false;
        }
        if (this.eewAlertPresent && channel.eewAlert() != this.eewAlert)
            return false;
        if (this.eewPredictionPresent && channel.eewPrediction() != this.eewPrediction)
            return false;
        if (this.eewDecimationPresent && channel.eewDecimation() != this.eewDecimation)
            return false;
        if (this.quakeInfoPresent && channel.quakeInfo() != this.quakeInfo)
            return false;
        if (this.tsunamiPresent && channel.tsunami() != this.tsunami)
            return false;
        if (this.intensityPresent) {
            SeismicIntensity minIntensity = channel.minIntensity();
            if (minIntensity == null || minIntensity.getCode() > this.intensity.getCode())
                return false;
        }
        if (this.webhookIdPresent) {
            if (channel.webhook() == null || channel.webhook().id() != this.webhookId)
                return false;
        }
        return true;
    }

    public static ChannelFilter.Builder builder() {
        return new ChannelFilter.Builder();
    }

    public static class Builder {

        private final ChannelFilter filter = new ChannelFilter();

        public Builder hasGuild(boolean hasGuild) {
            this.filter.hasGuild = hasGuild;
            this.filter.hasGuildPresent = true;
            return this;
        }

        public Builder guildId(long guildId) {
            this.filter.guildId = guildId;
            this.filter.guildIdPresent = true;
            return this;
        }

        public Builder channelId(long channelId) {
            this.filter.channelId = channelId;
            this.filter.channelIdPresent = true;
            return this;
        }

        public Builder threadId(Long threadId) {
            this.filter.threadId = threadId;
            this.filter.threadIdPresent = true;
            return this;
        }

        public Builder isThread(boolean isThread) {
            this.filter.isThread = isThread;
            this.filter.isThreadPresent = true;
            return this;
        }

        public Builder eewAlert(boolean eewAlert) {
            this.filter.eewAlert = eewAlert;
            this.filter.eewAlertPresent = true;
            return this;
        }

        public Builder eewPrediction(boolean eewPrediction) {
            this.filter.eewPrediction = eewPrediction;
            this.filter.eewPredictionPresent = true;
            return this;
        }

        public Builder eewDecimation(boolean eewDecimation) {
            this.filter.eewDecimation = eewDecimation;
            this.filter.eewDecimationPresent = true;
            return this;
        }

        public Builder quakeInfo(boolean quakeInfo) {
            this.filter.quakeInfo = quakeInfo;
            this.filter.quakeInfoPresent = true;
            return this;
        }

        public Builder tsunami(boolean tsunami) {
            this.filter.tsunami = tsunami;
            this.filter.tsunamiPresent = true;
            return this;
        }

        public Builder intensity(SeismicIntensity minIntensity) {
            this.filter.intensity = minIntensity;
            this.filter.intensityPresent = true;
            return this;
        }

        public Builder webhookId(long webhookId) {
            this.filter.webhookId = webhookId;
            this.filter.webhookIdPresent = true;
            return this;
        }

        public ChannelFilter build() {
            return this.filter;
        }
    }

}
