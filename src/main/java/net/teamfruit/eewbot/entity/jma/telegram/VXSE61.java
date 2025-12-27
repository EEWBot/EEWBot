package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.time.Instant;
import java.util.Optional;

public interface VXSE61 extends JMAReport, QuakeInfo, ExternalData {

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

    @Override
    default String getDataType() {
        return "quake_info";
    }

    @Override
    default Object toExternalDto() {
        return QuakeInfoExternalData.builder()
                .title(getHeadTitle())
                .dateTime(getDateTime() != null ? getDateTime().getEpochSecond() : 0)
                .status(getStatus() != null ? getStatus().toString() : null)
                .editorialOffice(getEditorialOffice())
                .publishingOffice(getPublishingOffice())
                .headTitle(getHeadTitle())
                .reportDateTime(getReportDateTime() != null ? getReportDateTime().getEpochSecond() : 0)
                .eventId(getEventId())
                .infoType(getInfoType() != null ? getInfoType().toString() : null)
                .serial(getSerial())
                .infoKind(getInfoKind())
                .infoKindVersion(getInfoKindVersion())
                .intensityAreas(new java.util.ArrayList<>())
                .build();
    }
}
