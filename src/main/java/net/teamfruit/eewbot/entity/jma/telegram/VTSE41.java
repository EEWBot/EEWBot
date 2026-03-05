package net.teamfruit.eewbot.entity.jma.telegram;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.TsunamiCategory;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.TsunamiExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Category;
import net.teamfruit.eewbot.entity.jma.telegram.seis.FirstHeight;
import net.teamfruit.eewbot.entity.jma.telegram.seis.MaxHeight;
import net.teamfruit.eewbot.entity.jma.telegram.seis.TsunamiItem;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface VTSE41 extends JMAReport, ExternalData {

    List<TsunamiItem> getForecastItems();

    Optional<Comment.CommentForm> getWarningComment();

    Optional<String> getFreeFormComment();

    @Override
    default <T> T createEmbed(String lang, IEmbedBuilder<T> builder) {
        builder.title("eewbot.tsunami.title");

        if (isCancelReport()) {
            builder.description("eewbot.tsunami.cancel");
            builder.color(TsunamiCategory.NONE.getColor());
        } else {
            List<TsunamiItem> items = getForecastItems();
            Color highestColor = TsunamiCategory.NONE.getColor();
            int highestPriority = -1;

            for (TsunamiItem item : items) {
                Category category = item.getCategory();
                if (category == null)
                    continue;

                TsunamiCategory tsunamiCategory = TsunamiCategory.fromCode(category.getKind().getCode());
                if (tsunamiCategory.getPriority() > highestPriority) {
                    highestPriority = tsunamiCategory.getPriority();
                    highestColor = tsunamiCategory.getColor();
                }

                String fieldName = category.getKind().getName() + " " + item.getArea().getName();
                StringBuilder fieldValue = new StringBuilder();

                MaxHeight maxHeight = item.getMaxHeight();
                if (maxHeight != null) {
                    if (maxHeight.getTsunamiHeight() != null && maxHeight.getTsunamiHeight().getDescription() != null) {
                        fieldValue.append(maxHeight.getTsunamiHeight().getDescription());
                    } else if (maxHeight.getCondition() != null) {
                        fieldValue.append(maxHeight.getCondition());
                    }
                }

                FirstHeight firstHeight = item.getFirstHeight();
                if (firstHeight != null) {
                    Instant arrivalTime = firstHeight.getArrivalTime();
                    if (arrivalTime != null) {
                        if (!fieldValue.isEmpty())
                            fieldValue.append("\n");
                        fieldValue.append("<t:").append(arrivalTime.getEpochSecond()).append(":f>");
                    } else if (firstHeight.getCondition() != null) {
                        if (!fieldValue.isEmpty())
                            fieldValue.append("\n");
                        fieldValue.append(firstHeight.getCondition());
                    }
                }

                if (fieldValue.isEmpty())
                    fieldValue.append("-");

                builder.addField(fieldName, fieldValue.toString(), false);
            }

            builder.color(highestColor);

            getWarningComment().ifPresent(comment -> builder.addField("", comment.getText(), false));
            getFreeFormComment().ifPresent(comment -> builder.addField("", comment, false));
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
