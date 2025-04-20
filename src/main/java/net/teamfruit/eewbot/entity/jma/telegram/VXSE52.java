package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.renderer.QuakeDataFactory;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import net.teamfruit.eewbot.registry.Config;

import java.time.Instant;
import java.util.Optional;

public interface VXSE52 extends JMAReport, QuakeInfo {

    Instant getOriginTime();

    String getHypocenterName();

    Coordinate getCoordinate();

    Intensity.IntensityDetail getObservation();

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

            Config config = EEWBot.instance.getConfig();
            if (config.isRendererAvailable()) {
                try {
                    builder.image(config.getRendererAddress() + QuakeDataFactory.generate(config.getRendererKey(), this));
                } catch (Exception e) {
                    builder.addField("Renderer Query", String.format("Failed to generate query: %s", e), false);
                }
            }
        }
        builder.footer(getPublishingOffice(), null);
        builder.timestamp(getReportDateTime());
        return builder.build();
    }

}
