package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.renderer.RenderQuakePrefecture;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.time.Instant;
import java.util.Optional;

public interface VXSE52 extends JMAReport, QuakeInfo, RenderQuakePrefecture {

    Instant getOriginTime();

    String getHypocenterName();

    Optional<String> getDepth();

    String getMagnitude();

    Optional<Comment.CommentForm> getForecastComment();

    Optional<String> getFreeFormComment();

    @Override
    default <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        builder.title("eewbot.quakeinfo.epicenter.title");
        if (isCancelReport()) {
            builder.description("eewbot.quakeinfo.epicenter.cancel");
            builder.color(SeismicIntensity.UNKNOWN.getColor());
        } else {
            builder.description("eewbot.quakeinfo.epicenter.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>");
            builder.addField("eewbot.quakeinfo.field.epicenter", getHypocenterName(), true);
            getDepth().ifPresent(depth -> builder.addField("eewbot.quakeinfo.field.depth", depth, true));
            builder.addField("eewbot.quakeinfo.field.magnitude", getMagnitude(), true);
            getForecastComment().ifPresent(forecastComment -> builder.addField("", forecastComment.getText(), false));
            getFreeFormComment().ifPresent(freeFormComment -> builder.addField("", freeFormComment, false));
            getQuakeInfoMaxInt().ifPresent(intensity -> builder.color(intensity.getColor()));

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
