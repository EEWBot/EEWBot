package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.ComponentContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.IComponentBuilder;
import net.teamfruit.eewbot.entity.discord.PendingComponent;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface VXSE61 extends JMAReport, QuakeInfo, ExternalData {

    Instant getOriginTime();

    String getHypocenterName();

    Optional<String> getDepth();

    String getMagnitude();

    Coordinate getCoordinate();

    Optional<String> getFreeFormComment();

    @Override
    default List<PendingComponent> createComponents(String lang, ComponentContext ctx, Supplier<IComponentBuilder> factory) {
        IComponentBuilder builder = factory.get();
        builder.heading("eewbot.quakeinfo.hypocenterupdate.title");
        if (isCancelReport()) {
            builder.text("eewbot.quakeinfo.hypocenterupdate.cancel");
            builder.accent(SeismicIntensity.UNKNOWN.getColor());
        } else {
            builder.text("eewbot.quakeinfo.hypocenterupdate.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>");
            builder.detail("eewbot.quakeinfo.field.epicenter", getHypocenterName());
            getDepth().ifPresent(depth -> builder.detail("eewbot.quakeinfo.field.depth", depth));
            builder.detail("eewbot.quakeinfo.field.magnitude", getMagnitude());
            getQuakeInfoMaxInt().ifPresent(intensity -> builder.accent(intensity.getColor()));
        }
        builder.footer(getPublishingOffice());
        builder.timestamp(getReportDateTime());
        return List.of(builder.build());
    }

    @Override
    default String getDataType() {
        return "quake_info";
    }

    @Override
    default Object toExternalDto() {
        Coordinate coord = !isCancelReport() ? getCoordinate() : null;

        return QuakeInfoExternalData.builder()
                // Control
                .title(getHeadTitle())
                .dateTime(getDateTime() != null ? getDateTime().getEpochSecond() : 0)
                .status(getStatus() != null ? getStatus().toString() : null)
                .editorialOffice(getEditorialOffice())
                .publishingOffice(getPublishingOffice())
                // Head
                .reportDateTime(getReportDateTime() != null ? getReportDateTime().getEpochSecond() : 0)
                .eventId(getEventId())
                .infoType(getInfoType() != null ? getInfoType().toString() : null)
                .serial(getSerial())
                // 震度情報（VXSE61にはない）
                .maxInt(null)
                .intensities(null)
                // 震源情報
                .originTime(!isCancelReport() ? getOriginTime().getEpochSecond() : null)
                .hypocenterName(!isCancelReport() ? getHypocenterName() : null)
                .hypocenterDetailedName(null)
                .latitude(coord != null ? coord.getLat() : null)
                .longitude(coord != null ? coord.getLon() : null)
                .depth(getDepth().orElse(null))
                .magnitude(!isCancelReport() ? getMagnitude() : null)
                // コメント
                .forecastComment(null)
                .freeFormComment(getFreeFormComment().orElse(null))
                .build();
    }
}
