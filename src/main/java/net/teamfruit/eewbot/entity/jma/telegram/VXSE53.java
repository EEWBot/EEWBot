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
import net.teamfruit.eewbot.entity.jma.telegram.seis.IntensityPref;
import net.teamfruit.eewbot.entity.renderer.RenderQuakePrefecture;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface VXSE53 extends JMAReport, QuakeInfo, RenderQuakePrefecture, ExternalData {

    Instant getOriginTime();

    SeismicIntensity getMaxInt();

    String getHypocenterName();

    Optional<String> getHypocenterDetailedName();

    Optional<String> getDepth();

    String getMagnitude();

    Optional<Comment.CommentForm> getForecastComment();

    Optional<Comment.CommentForm> getVarComment();

    Optional<String> getFreeFormComment();

    @Override
    default List<PendingComponent> createComponents(String lang, ComponentContext ctx) {
        ContainerBuilder builder = PendingComponent.Container.builder();
        if (isCancelReport()) {
            builder.textDisplay("# " + ctx.i18n().get(lang, "eewbot.quakeinfo.detail.title"));
            builder.textDisplay(ctx.i18n().get(lang, "eewbot.quakeinfo.detail.cancel"));
            builder.accentColor(SeismicIntensity.UNKNOWN.getColor());
        } else if (getHeadTitle().equals("遠地地震に関する情報")) {
            getFreeFormComment().filter(text -> text.contains("噴火が発生")).ifPresentOrElse(text -> {
                // 海外噴火
                builder.textDisplay("# " + ctx.i18n().get(lang, "eewbot.quakeinfo.detail.eruption.title"));
                getHypocenterDetailedName().ifPresentOrElse(detailedName -> builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.area") + "**\n" + detailedName),
                        () -> builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.area") + "**\n" + getHypocenterName()));
                builder.textDisplay(StringUtils.substringBefore(text, "（注"));
            }, () -> {
                // 海外地震
                builder.textDisplay("# " + ctx.i18n().get(lang, "eewbot.quakeinfo.detail.overseas.title"));
                builder.textDisplay(ctx.i18n().format(lang, "eewbot.quakeinfo.detail.overseas.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>"));
                getHypocenterDetailedName().ifPresentOrElse(detailedName -> builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.epicenter") + "**\n" + detailedName),
                        () -> builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.epicenter") + "**\n" + getHypocenterName()));
                builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.magnitude") + "**\n" + getMagnitude());
                getFreeFormComment().ifPresent(builder::textDisplay);
            });
            getForecastComment().ifPresent(forecastComment -> builder.textDisplay(forecastComment.getText()));
        } else {
            builder.textDisplay("# " + ctx.i18n().get(lang, "eewbot.quakeinfo.detail.title"));
            builder.textDisplay(ctx.i18n().format(lang, "eewbot.quakeinfo.detail.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>"));
            builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.epicenter") + "**\n" + getHypocenterName());
            getDepth().ifPresent(depth -> builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.depth") + "**\n" + depth));
            builder.textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.magnitude") + "**\n" + getMagnitude());
            builder.separator().textDisplay("**" + ctx.i18n().get(lang, "eewbot.quakeinfo.field.maxintensity") + "**\n" + getMaxInt().getSimple());
            getForecastComment().ifPresent(forecastComment -> builder.textDisplay(forecastComment.getText()));
            getVarComment().map(varComment -> varComment.getText().replace("＊印は気象庁以外の震度観測点についての情報です。", ""))
                    .filter(StringUtils::isNotBlank)
                    .ifPresent(builder::textDisplay);
            getFreeFormComment().ifPresent(builder::textDisplay);
            builder.accentColor(getMaxInt().getColor());

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
        List<QuakeInfoExternalData.IntensityAreaInfo> intensityList = null;
        String maxIntStr = null;
        Coordinate coord = null;

        if (!isCancelReport()) {
            maxIntStr = getMaxInt() != null ? getMaxInt().getSymbolIntensity() : null;
            coord = getCoordinate();

            intensityList = new ArrayList<>();
            for (IntensityPref pref : getIntensityDetail().getIntensityPref()) {
                for (var area : pref.getAreas()) {
                    intensityList.add(QuakeInfoExternalData.IntensityAreaInfo.builder()
                            .prefName(pref.getName())
                            .prefCode(pref.getCode())
                            .areaName(area.getName())
                            .areaCode(area.getCode())
                            .maxInt(area.getMaxInt() != null ? area.getMaxInt().getSymbolIntensity() : null)
                            .build());
                }
            }
        }

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
                // 震度情報
                .maxInt(maxIntStr)
                .intensities(intensityList)
                // 震源情報
                .originTime(!isCancelReport() ? getOriginTime().getEpochSecond() : null)
                .hypocenterName(!isCancelReport() ? getHypocenterName() : null)
                .hypocenterDetailedName(getHypocenterDetailedName().orElse(null))
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
