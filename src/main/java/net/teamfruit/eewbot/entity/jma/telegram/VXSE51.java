package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.seis.IntensityPref;
import net.teamfruit.eewbot.entity.renderer.RenderQuakePrefecture;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VXSE51 extends JMAReport, QuakeInfo, RenderQuakePrefecture {

    Instant getTargetDateTime();

    SeismicIntensity getMaxInt();

    List<IntensityPref> getPrefs();

    Optional<Comment.CommentForm> getForecastComment();

    Optional<String> getFreeFormComment();

    @Override
    default <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        builder.title("eewbot.quakeinfo.intensity.title");
        if (isCancelReport()) {
            builder.description("eewbot.quakeinfo.intensity.cancel");
            builder.color(SeismicIntensity.UNKNOWN.getColor());
        } else {
            builder.description("eewbot.quakeinfo.intensity.desc", "<t:" + getTargetDateTime().getEpochSecond() + ":f>");
            Map<SeismicIntensity, StringBuilder> intensityMap = new EnumMap<>(SeismicIntensity.class);
            getPrefs().stream().flatMap(pref -> pref.getAreas().stream())
                    .forEach(area -> {
                        StringBuilder sb = intensityMap.computeIfAbsent(area.getMaxInt(), k -> new StringBuilder());
                        if (sb.length() > 0)
                            sb.append(" ");
                        sb.append(area.getName());
                    });

            SeismicIntensity[] intensities = SeismicIntensity.values();
            for (int i = intensities.length - 1; i >= 0; i--) {
                SeismicIntensity line = intensities[i];
                StringBuilder sb = intensityMap.get(line);
                if (sb != null) {
                    builder.addField("eewbot.quakeinfo.field.intensity", sb.toString(), false, line.getSimple());
                }
            }
            getForecastComment().ifPresent(forecastComment -> builder.addField("", forecastComment.getText(), false));
            getFreeFormComment().ifPresent(freeFormComment -> builder.addField("", freeFormComment, false));
            builder.color(getMaxInt().getColor());

            if (EEWBot.instance.getRendererQueryFactory().isAvailable()) {
                try {
                    builder.image(EEWBot.instance.getRendererQueryFactory().generateURL( this));
                } catch (Exception e) {
                    Log.logger.error("Failed to generate renderer query", e);
                }
            }
        }
        builder.footer(getPublishingOffice(), null);
        builder.timestamp(getReportDateTime());
        return builder.build();
    }

}
