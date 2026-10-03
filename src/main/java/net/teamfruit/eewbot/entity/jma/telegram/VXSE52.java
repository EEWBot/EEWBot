package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.ComponentContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.ContainerBuilder;
import net.teamfruit.eewbot.entity.discord.PendingComponent;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.renderer.RenderQuakePrefecture;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VXSE52 extends JMAReport, QuakeInfo, RenderQuakePrefecture, ExternalData {

    Instant getOriginTime();

    String getHypocenterName();

    Optional<String> getDepth();

    String getMagnitude();

    Optional<Comment.CommentForm> getForecastComment();

    Optional<String> getFreeFormComment();

    @Override
    default List<PendingComponent> createComponents(String lang, ComponentContext ctx) {
        ContainerBuilder builder = PendingComponent.Container.builder();
        builder.textDisplay("# " + ctx.i18n().get(lang, "eewbot.quakeinfo.epicenter.title"));
        if (isCancelReport()) {
            builder.textDisplay(ctx.i18n().get(lang, "eewbot.quakeinfo.epicenter.cancel"));
            builder.accentColor(SeismicIntensity.UNKNOWN.getColor());
        } else {
            builder.textDisplay(ctx.i18n().format(lang, "eewbot.quakeinfo.epicenter.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>"));
            builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.epicenter") + "**\n" + getHypocenterName());
            getDepth().ifPresent(depth -> builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.depth") + "**\n" + depth));
            builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.magnitude") + "**\n" + getMagnitude());
            getForecastComment().ifPresent(forecastComment -> builder.textDisplay(forecastComment.getText()));
            getFreeFormComment().ifPresent(builder::textDisplay);
            getQuakeInfoMaxInt().ifPresent(intensity -> builder.accentColor(intensity.getColor()));

            if (ctx.renderer().isAvailable()) {
                try {
                    builder.separator().mediaGallery(PendingComponent.MediaGalleryItem.of(ctx.renderer().generateURL(this), null, false));
                } catch (Exception e) {
                    Log.logger.error("Failed to generate renderer query", e);
                }
            }
        }
        builder.textDisplay("-# " + ctx.i18n().get(lang, getPublishingOffice())
                + " • <t:" + getReportDateTime().getEpochSecond() + ":F>");
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
                // 震度情報（VXSE52にはない）
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
                .forecastComment(getForecastComment().map(Comment.CommentForm::getText).orElse(null))
                .freeFormComment(getFreeFormComment().orElse(null))
                .build();
    }
}
