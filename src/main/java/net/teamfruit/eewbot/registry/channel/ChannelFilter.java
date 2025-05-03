package net.teamfruit.eewbot.registry.channel;

import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.util.Objects;

public class ChannelFilter {

    private Boolean isGuild;
    private boolean isGuildPresent;
    private long guildId;
    private boolean guildIdPresent;
    private boolean eewAlert;
    private boolean eewAlertPresent;
    private boolean eewPrediction;
    private boolean eewPredictionPresent;
    private boolean eewDecimation;
    private boolean eewDecimationPresent;
    private boolean quakeInfo;
    private boolean quakeInfoPresent;
    private SeismicIntensity intensity;
    private boolean intensityPresent;
    private long webhookId;
    private boolean webhookIdPresent;

    public boolean test(Channel channel) {
        if (this.isGuildPresent && !Objects.equals(channel.isGuild(), this.isGuild))
            return false;
        if (this.guildIdPresent && !Objects.equals(channel.getGuildId(), this.guildId))
            return false;
        if (this.eewAlertPresent && channel.isEewAlert() != this.eewAlert)
            return false;
        if (this.eewPredictionPresent && channel.isEewPrediction() != this.eewPrediction)
            return false;
        if (this.eewDecimationPresent && channel.isEewDecimation() != this.eewDecimation)
            return false;
        if (this.quakeInfoPresent && channel.isQuakeInfo() != this.quakeInfo)
            return false;
        if (this.intensityPresent && channel.getMinIntensity().ordinal() > this.intensity.ordinal())
            return false;
        if (this.webhookIdPresent && channel.getWebhook() == null)
            return false;
        return !this.webhookIdPresent || channel.getWebhook().getId() == this.webhookId;
    }

    public String toQueryString() {
        StringBuilder builder = new StringBuilder();
        if (this.isGuildPresent) {
            if (this.isGuild != null)
                builder.append("@isGuild:{").append(this.isGuild).append("} ");
            else
                builder.append("-@isGuild:{true | false} ");
        }
        if (this.guildIdPresent)
            builder.append("@guildId:[").append(this.guildId).append(" ").append(this.guildId).append("] ");
        if (this.eewAlertPresent) {
            if (this.eewAlert)
                builder.append("@flags:{eewAlert} ");
            else
                builder.append("-@flags:{eewAlert} ");
        }
        if (this.eewPredictionPresent) {
            if (this.eewPrediction)
                builder.append("@flags:{eewPrediction} ");
            else
                builder.append("-@flags:{eewPrediction} ");
        }
        if (this.eewDecimationPresent) {
            if (this.eewDecimation)
                builder.append("@flags:{eewDecimation} ");
            else
                builder.append("-@flags:{eewDecimation} ");
        }
        if (this.quakeInfoPresent) {
            if (this.quakeInfo)
                builder.append("@flags:{quakeInfo} ");
            else
                builder.append("-@flags:{quakeInfo} ");
        }
        if (this.intensityPresent)
            builder.append("@minIntensity:[0 ").append(this.intensity.ordinal()).append("] ");
        if (this.webhookIdPresent)
            builder.append("@webhookId:[").append(this.webhookId).append(" ").append(this.webhookId).append("]");
        return builder.toString().trim();
    }

    public static ChannelFilter.Builder builder() {
        return new ChannelFilter.Builder();
    }

    public static class Builder {

        private final ChannelFilter filter = new ChannelFilter();

        public Builder isGuild(Boolean isGuild) {
            this.filter.isGuild = isGuild;
            this.filter.isGuildPresent = true;
            return this;
        }

        public Builder guildId(long guildId) {
            this.filter.guildId = guildId;
            this.filter.guildIdPresent = true;
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
