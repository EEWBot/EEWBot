package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.ComponentContext;
import net.teamfruit.eewbot.entity.TsunamiCategory;
import net.teamfruit.eewbot.entity.discord.IComponentBuilder;
import net.teamfruit.eewbot.entity.discord.PendingComponent;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.TsunamiExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Category;
import net.teamfruit.eewbot.entity.jma.telegram.seis.FirstHeight;
import net.teamfruit.eewbot.entity.jma.telegram.seis.MaxHeight;
import net.teamfruit.eewbot.entity.jma.telegram.seis.TsunamiItem;
import net.teamfruit.eewbot.entity.renderer.RenderTsunami;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public interface VTSE41 extends JMAReport, RenderTsunami, ExternalData {

    @Override
    List<TsunamiItem> getForecastItems();

    Optional<String> getText();

    Optional<Comment.CommentForm> getWarningComment();

    Optional<String> getFreeFormComment();

    @Override
    @SuppressWarnings("NonAsciiCharacters")
    default List<PendingComponent> createComponents(String lang, ComponentContext ctx, Supplier<IComponentBuilder> factory) {
        IComponentBuilder builder = factory.get();
        if (isCancelReport()) {
            builder.heading("eewbot.tsunami.title");
            getText().ifPresentOrElse(builder::rawText, () -> builder.text("eewbot.tsunami.cancel"));
            builder.accent(TsunamiCategory.津波なし.getColor());
        } else {
            List<TsunamiItem> items = getForecastItems();
            TsunamiCategory highest = null;

            LinkedHashMap<String, List<String>> groupedAreas = new LinkedHashMap<>();

            for (TsunamiItem item : items) {
                Category category = item.getCategory();
                if (category == null)
                    continue;

                TsunamiCategory tsunamiCategory = TsunamiCategory.fromCode(category.getKind().getCode());
                if (highest == null || tsunamiCategory.getLevel() > highest.getLevel()) {
                    highest = tsunamiCategory;
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

            builder.heading(highest != null ? highest.getTitleKey() : "eewbot.tsunami.title");
            builder.accent(highest != null ? highest.getColor() : TsunamiCategory.津波なし.getColor());

            for (Map.Entry<String, List<String>> entry : groupedAreas.entrySet()) {
                builder.detail(entry.getKey(), String.join("\n", entry.getValue()));
            }

            if (highest != null && highest.getLevel() > 0 && ctx.renderer().isAvailable()) {
                try {
                    builder.separator().media(ctx.renderer().generateURL(this), null);
                } catch (Exception e) {
                    Log.logger.error("Failed to generate renderer query", e);
                }
            }

            getFreeFormComment().ifPresent(builder::rawText);
            getText().ifPresent(builder::rawText);
        }

        builder.footer(getPublishingOffice());
        builder.timestamp(getReportDateTime());
        return List.of(builder.build());
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
