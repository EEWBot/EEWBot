package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import redis.clients.jedis.search.Query;

public class ChannelFilter {

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

    public Query toQuery() {
        StringBuilder builder = new StringBuilder();
        if (this.eewAlertPresent)
            builder.append("@eewAlert:{").append(this.eewAlert).append("} ");
        if (this.eewPredictionPresent)
            builder.append("@eewPrediction:{").append(this.eewPrediction).append("} ");
        if (this.eewDecimationPresent)
            builder.append("@eewDecimation:{").append(this.eewDecimation).append("} ");
        if (this.quakeInfoPresent)
            builder.append("@quakeInfo:{").append(this.quakeInfo).append("} ");
        if (this.intensityPresent)
            builder.append("@minIntensity:{").append(this.intensity.getLegacySerializedName()).append("} ");
        if (this.webhookIdPresent)
            builder.append("@webhookId:[").append(this.webhookId).append(" ").append(this.webhookId).append("]");
        return new Query(builder.toString());
    }

    public static ChannelFilter.Builder builder() {
        return new ChannelFilter.Builder();
    }

    public static class Builder {

        private final ChannelFilter filter = new ChannelFilter();

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
