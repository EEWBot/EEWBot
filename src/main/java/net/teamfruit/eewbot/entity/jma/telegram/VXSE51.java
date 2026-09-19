package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.ComponentContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.IComponentBuilder;
import net.teamfruit.eewbot.entity.discord.PendingComponent;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.seis.IntensityPref;
import net.teamfruit.eewbot.entity.renderer.RenderQuakePrefecture;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public interface VXSE51 extends JMAReport, QuakeInfo, RenderQuakePrefecture, ExternalData {

    Instant getTargetDateTime();

    SeismicIntensity getMaxInt();

    List<IntensityPref> getPrefs();

    Optional<Comment.CommentForm> getForecastComment();

    Optional<String> getFreeFormComment();

    @Override
    default List<PendingComponent> createComponents(String lang, ComponentContext ctx, Supplier<IComponentBuilder> factory) {
        IComponentBuilder builder = factory.get();
        builder.heading("eewbot.quakeinfo.intensity.title");
        if (isCancelReport()) {
            builder.text("eewbot.quakeinfo.intensity.cancel");
            builder.accent(SeismicIntensity.UNKNOWN.getColor());
        } else {
            builder.text("eewbot.quakeinfo.intensity.desc", "<t:" + getTargetDateTime().getEpochSecond() + ":f>");
            Map<SeismicIntensity, StringBuilder> intensityMap = new EnumMap<>(SeismicIntensity.class);
            getPrefs().stream().flatMap(pref -> pref.getAreas().stream())
                    .forEach(area -> {
                        StringBuilder sb = intensityMap.computeIfAbsent(area.getMaxInt(), k -> new StringBuilder());
                        if (!sb.isEmpty())
                            sb.append(" ");
                        sb.append(area.getName());
                    });

            SeismicIntensity[] intensities = SeismicIntensity.values();
            for (int i = intensities.length - 1; i >= 0; i--) {
                SeismicIntensity line = intensities[i];
                StringBuilder sb = intensityMap.get(line);
                if (sb != null) {
                    builder.detail("eewbot.quakeinfo.field.intensity", sb.toString(), line.getSimple());
                }
            }
            getForecastComment().ifPresent(forecastComment -> builder.rawText(forecastComment.getText()));
            getFreeFormComment().ifPresent(builder::rawText);
            builder.accent(getMaxInt().getColor());

            if (ctx.renderer().isAvailable()) {
                try {
                    builder.separator().media(ctx.renderer().generateURL(this), null);
                } catch (Exception e) {
                    Log.logger.error("Failed to generate renderer query", e);
                }
            }
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
        List<QuakeInfoExternalData.IntensityAreaInfo> intensityList = null;
        String maxIntStr = null;
        if (!isCancelReport()) {
            maxIntStr = getMaxInt() != null ? getMaxInt().getSymbolIntensity() : null;
            intensityList = new ArrayList<>();
            for (IntensityPref pref : getPrefs()) {
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
                // 震源情報（VXSE51にはない）
                .originTime(null)
                .hypocenterName(null)
                .hypocenterDetailedName(null)
                .latitude(null)
                .longitude(null)
                .depth(null)
                .magnitude(null)
                // コメント
                .forecastComment(getForecastComment().map(Comment.CommentForm::getText).orElse(null))
                .freeFormComment(getFreeFormComment().orElse(null))
                .build();
    }
}
