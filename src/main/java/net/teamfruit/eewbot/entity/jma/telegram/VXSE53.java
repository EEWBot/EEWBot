package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.renderer.RenderQuakePrefecture;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Optional;

public interface VXSE53 extends JMAReport, QuakeInfo, RenderQuakePrefecture {

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
    default <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        if (isCancelReport()) {
            builder.title("eewbot.quakeinfo.detail.title");
            builder.description("eewbot.quakeinfo.detail.cancel");
            builder.color(SeismicIntensity.UNKNOWN.getColor());
        } else if (getHeadTitle().equals("遠地地震に関する情報")) {
            getFreeFormComment().filter(text -> text.contains("噴火が発生")).ifPresentOrElse(text -> {
                // 海外噴火
                builder.title("eewbot.quakeinfo.detail.eruption.title");
                getHypocenterDetailedName().ifPresentOrElse(detailedName -> builder.addField("eewbot.quakeinfo.field.area", detailedName, true),
                        () -> builder.addField("eewbot.quakeinfo.field.area", getHypocenterName(), true));
                builder.addField("", StringUtils.substringBefore(text, "（注"), false);
            }, () -> {
                // 海外地震
                builder.title("eewbot.quakeinfo.detail.overseas.title");
                builder.description("eewbot.quakeinfo.detail.overseas.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>");
                getHypocenterDetailedName().ifPresentOrElse(detailedName -> builder.addField("eewbot.quakeinfo.field.epicenter", detailedName, true),
                        () -> builder.addField("eewbot.quakeinfo.field.epicenter", getHypocenterName(), true));
                builder.addField("eewbot.quakeinfo.field.magnitude", getMagnitude(), true);
                getFreeFormComment().ifPresent(freeFormComment -> builder.addField("", freeFormComment, false));
            });
            getForecastComment().ifPresent(forecastComment -> builder.addField("", forecastComment.getText(), false));
        } else {
            builder.title("eewbot.quakeinfo.detail.title");
            builder.description("eewbot.quakeinfo.detail.desc", "<t:" + getOriginTime().getEpochSecond() + ":f>");
            builder.addField("eewbot.quakeinfo.field.epicenter", getHypocenterName(), true);
            getDepth().ifPresent(depth -> builder.addField("eewbot.quakeinfo.field.depth", depth, true));
            builder.addField("eewbot.quakeinfo.field.magnitude", getMagnitude(), true);
            builder.addField("eewbot.quakeinfo.field.maxintensity", getMaxInt().getSimple(), true);
            getForecastComment().ifPresent(forecastComment -> builder.addField("", forecastComment.getText(), false));
            getVarComment().map(varComment -> varComment.getText().replace("＊印は気象庁以外の震度観測点についての情報です。", ""))
                    .filter(StringUtils::isNotBlank)
                    .ifPresent(text -> builder.addField("", text, false));
            getFreeFormComment().ifPresent(freeFormComment -> builder.addField("", freeFormComment, false));
            builder.color(getMaxInt().getColor());

            if (EEWBot.instance.getRendererQueryFactory().isAvailable()) {
                try {
                    builder.image(EEWBot.instance.getRendererQueryFactory().generateURL( this));
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
