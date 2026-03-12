package net.teamfruit.eewbot.entity.jma.telegram;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.TsunamiCategory;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.TsunamiExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Category;
import net.teamfruit.eewbot.entity.jma.telegram.seis.FirstHeight;
import net.teamfruit.eewbot.entity.jma.telegram.seis.MaxHeight;
import net.teamfruit.eewbot.entity.jma.telegram.seis.TsunamiItem;
import net.teamfruit.eewbot.entity.renderer.RenderTsunami;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.*;

public interface VTSE41 extends JMAReport, RenderTsunami, ExternalData {

    @Override
    List<TsunamiItem> getForecastItems();

    Optional<String> getText();

    Optional<Comment.CommentForm> getWarningComment();

    Optional<String> getFreeFormComment();

    @Override
    @SuppressWarnings("NonAsciiCharacters")
    default <T> T createEmbed(String lang, I18n i18n, IEmbedBuilder<T> builder) {
        builder.title("eewbot.tsunami.title");

        if (isCancelReport()) {
            getText().ifPresentOrElse(builder::description, () -> builder.description("eewbot.tsunami.cancel"));
            builder.color(TsunamiCategory.津波なし.getColor());
        } else {
            List<TsunamiItem> items = getForecastItems();
            Color highestColor = TsunamiCategory.津波なし.getColor();
            int highestPriority = -1;

            // Group items by category kind name to avoid exceeding Discord's 25-field limit
            LinkedHashMap<String, List<String>> groupedAreas = new LinkedHashMap<>();

            for (TsunamiItem item : items) {
                Category category = item.getCategory();
                if (category == null)
                    continue;

                TsunamiCategory tsunamiCategory = TsunamiCategory.fromCode(category.getKind().getCode());
                if (tsunamiCategory.getLevel() > highestPriority) {
                    highestPriority = tsunamiCategory.getLevel();
                    highestColor = tsunamiCategory.getColor();
                }

                String categoryName = category.getKind().getName();
                StringBuilder line = new StringBuilder(item.getArea().getName());

                String maxHeightStr = null;
                MaxHeight maxHeight = item.getMaxHeight();
                if (maxHeight != null) {
                    if (maxHeight.getTsunamiHeight() != null && maxHeight.getTsunamiHeight().getDescription() != null) {
                        maxHeightStr = maxHeight.getTsunamiHeight().getDescription();
                    } else if (maxHeight.getCondition() != null) {
                        maxHeightStr = maxHeight.getCondition();
                    }
                }

                String firstHeightStr = null;
                FirstHeight firstHeight = item.getFirstHeight();
                if (firstHeight != null) {
                    Instant arrivalTime = firstHeight.getArrivalTime();
                    if (arrivalTime != null) {
                        firstHeightStr = "<t:" + arrivalTime.getEpochSecond() + ":f>";
                    } else if (firstHeight.getCondition() != null) {
                        firstHeightStr = firstHeight.getCondition();
                    }
                }

                if (StringUtils.isNotEmpty(maxHeightStr) && StringUtils.isNotEmpty(firstHeightStr)) {
                    line.append(": ").append(maxHeightStr).append(" / ").append(firstHeightStr);
                } else if (StringUtils.isNotEmpty(maxHeightStr)) {
                    line.append(": ").append(maxHeightStr);
                } else if (StringUtils.isNotEmpty(firstHeightStr)) {
                    line.append(": ").append(firstHeightStr);
                }

                groupedAreas.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(line.toString());
            }

            for (Map.Entry<String, List<String>> entry : groupedAreas.entrySet()) {
                String fieldName = entry.getKey();
                List<String> lines = entry.getValue();

                // Split into multiple fields if value exceeds Discord's 1024 char limit
                StringBuilder fieldValue = new StringBuilder();
                boolean first = true;
                for (String areaLine : lines) {
                    if (!first && fieldValue.length() + 1 + areaLine.length() > 1024) {
                        builder.addField(fieldName, fieldValue.toString(), false);
                        fieldValue = new StringBuilder();
                        fieldName = "";
                    }
                    if (!fieldValue.isEmpty())
                        fieldValue.append("\n");
                    fieldValue.append(areaLine);
                    first = false;
                }
                if (!fieldValue.isEmpty()) {
                    builder.addField(fieldName, fieldValue.toString(), false);
                }
            }

            builder.color(highestColor);

            if (highestPriority > 0 && EEWBot.instance.getRendererQueryFactory().isAvailable()) {
                try {
                    builder.image(EEWBot.instance.getRendererQueryFactory().generateURL(this));
                } catch (Exception e) {
                    Log.logger.error("Failed to generate renderer query", e);
                }
            }

//            getWarningComment().ifPresent(comment -> builder.addField("", comment.getText(), false));
            getFreeFormComment().ifPresent(comment -> builder.addField("", comment, false));
            getText().ifPresent(text -> builder.addField("", text, false));
        }

        builder.footer(getPublishingOffice(), null);
        builder.timestamp(getReportDateTime());
        return builder.build();
    }

    @Override
    default String getDataType() {
        return "tsunami";
    }

    @Override
    default Object toExternalDto() {
        List<TsunamiExternalData.ForecastAreaInfo> forecastAreas = null;

        if (!isCancelReport()) {
            forecastAreas = new ArrayList<>();
            for (TsunamiItem item : getForecastItems()) {
                TsunamiExternalData.ForecastAreaInfo.Builder areaBuilder = TsunamiExternalData.ForecastAreaInfo.builder()
                        .areaName(item.getArea().getName())
                        .areaCode(item.getArea().getCode());

                Category category = item.getCategory();
                if (category != null) {
                    areaBuilder.categoryName(category.getKind().getName())
                            .categoryCode(category.getKind().getCode());
                }

                MaxHeight maxHeight = item.getMaxHeight();
                if (maxHeight != null && maxHeight.getTsunamiHeight() != null) {
                    areaBuilder.maxHeightDescription(maxHeight.getTsunamiHeight().getDescription());
                }

                FirstHeight firstHeight = item.getFirstHeight();
                if (firstHeight != null && firstHeight.getArrivalTime() != null) {
                    areaBuilder.arrivalTime(firstHeight.getArrivalTime().getEpochSecond());
                }

                forecastAreas.add(areaBuilder.build());
            }
        }

        return TsunamiExternalData.builder()
                .title(getHeadTitle())
                .dateTime(getDateTime() != null ? getDateTime().getEpochSecond() : 0)
                .status(getStatus() != null ? getStatus().toString() : null)
                .editorialOffice(getEditorialOffice())
                .publishingOffice(getPublishingOffice())
                .reportDateTime(getReportDateTime() != null ? getReportDateTime().getEpochSecond() : 0)
                .eventId(getEventId())
                .infoType(getInfoType() != null ? getInfoType().toString() : null)
                .serial(getSerial())
                .forecastAreas(forecastAreas)
                .warningComment(getWarningComment().map(Comment.CommentForm::getText).orElse(null))
                .freeFormComment(getFreeFormComment().orElse(null))
                .build();
    }
}
