package net.teamfruit.eewbot.registry.destination.model;

import net.teamfruit.eewbot.entity.SeismicIntensity;

@SuppressWarnings("FieldMayBeFinal")
public class Channel extends ChannelBase {

    private boolean eewAlert;
    private boolean eewPrediction;
    private boolean quakeInfo;
    private boolean tsunami;
    private boolean eewDecimation;
    private SeismicIntensity minIntensity;

    public Channel(final Long guildId, final Long channelId, final Long threadId, final boolean eewAlert, final boolean eewPrediction, final boolean eewDecimation, final boolean quakeInfo, final boolean tsunami, final SeismicIntensity minIntensity, ChannelWebhook webhook, String lang) {
        super(guildId, channelId, threadId, webhook, lang);
        this.eewAlert = eewAlert;
        this.eewPrediction = eewPrediction;
        this.eewDecimation = eewDecimation;
        this.quakeInfo = quakeInfo;
        this.tsunami = tsunami;
        this.minIntensity = minIntensity;
    }

    public static Channel createDefault(Long guildId, Long channelId, Long threadId, String lang) {
        return new Channel(guildId, channelId, threadId, false, false, false, false, false, SeismicIntensity.UNKNOWN, null, lang);
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

    public boolean isTsunami() {
        return this.tsunami;
    }

    public SeismicIntensity getMinIntensity() {
        return this.minIntensity;
    }

    public void setMinIntensity(SeismicIntensity minIntensity) {
        this.minIntensity = minIntensity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Channel channel = (Channel) o;
        return this.eewAlert == channel.eewAlert
                && this.eewPrediction == channel.eewPrediction
                && this.eewDecimation == channel.eewDecimation
                && this.quakeInfo == channel.quakeInfo
                && this.tsunami == channel.tsunami
                && this.minIntensity == channel.minIntensity;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (this.eewAlert ? 1 : 0);
        result = 31 * result + (this.eewPrediction ? 1 : 0);
        result = 31 * result + (this.eewDecimation ? 1 : 0);
        result = 31 * result + (this.quakeInfo ? 1 : 0);
        result = 31 * result + (this.tsunami ? 1 : 0);
        result = 31 * result + (this.minIntensity != null ? this.minIntensity.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Channel{" +
                "eewAlert=" + this.eewAlert +
                ", eewPrediction=" + this.eewPrediction +
                ", eewDecimation=" + this.eewDecimation +
                ", quakeInfo=" + this.quakeInfo +
                ", tsunami=" + this.tsunami +
                ", minIntensity=" + this.minIntensity +
                '}';
    }

}
