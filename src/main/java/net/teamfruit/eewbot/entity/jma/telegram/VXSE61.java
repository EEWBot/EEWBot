package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.time.Instant;
import java.util.Optional;

public interface VXSE61 extends JMAReport, QuakeInfo {

    Instant getOriginTime();

    String getHypocenterName();

    Optional<String> getDepth();

    String getMagnitude();

    default <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        builder.title("eewbot.quakeinfo.hypocenterupdate.title");
        if (isCancelReport()) {
            builder.description("eewbot.quakeinfo.hypocenterupdate.cancel");
            builder.color(SeismicIntensity.UNKNOWN.getColor());
        } else {
            builder.description("eewbot.quakeinfo.hypocenterupdate.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>");
            builder.addField("eewbot.quakeinfo.field.epicenter", getHypocenterName(), true);
            getDepth().ifPresent(depth -> builder.addField("eewbot.quakeinfo.field.depth", depth, true));
            builder.addField("eewbot.quakeinfo.field.magnitude", getMagnitude(), true);
            getQuakeInfoMaxInt().ifPresent(intensity -> builder.color(intensity.getColor()));
        }
        builder.footer(getPublishingOffice(), null);
        builder.timestamp(getReportDateTime());
        return builder.build();
    }
}
