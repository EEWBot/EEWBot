package net.teamfruit.eewbot.entity.jma.telegram;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.ComponentContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.IComponentBuilder;
import net.teamfruit.eewbot.entity.discord.PendingComponent;
import net.teamfruit.eewbot.entity.external.EEWExternalData;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface EEW extends JMAReport, ExternalData {

    DateTimeFormatter REPORT_DATE_TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Tokyo"));

    boolean isConcurrent();

    void setConcurrent(boolean concurrent);

    int getConcurrentIndex();

    void setConcurrentIndex(int concurrentIndex);

    boolean isLastInfo();

    boolean isEEWWarning();

    boolean hasEarthquake();

    @Nullable
    String getCondition();

    @Nullable
    String getHypocenterName();

    @Nullable
    String getDepthValue();

    @Nullable
    String getDepthCondition();

    @Nullable
    String getMagnitudeValue();

    List<Integer> getEpicenterAccuracyRanks();

    int getDepthAccuracyRank();

    int getMagnitudeCalculationRank();

    int getNumberOfMagnitudeCalculation();

    List<WarningRegion> getWarningRegions();

    @Nullable
    ForecastMaxInt getForecastMaxInt();

    @Nullable
    List<ForecastRegion> getForecastRegions();

    @Nullable
    String getText();

    record WarningRegion(String name, @Nullable String code, @Nullable String kindCode,
                         @Nullable String lastKindCode) {
    }

    record ForecastMaxInt(@Nullable String from, @Nullable String to) {
    }

    record ForecastRegion(String name, ForecastMaxInt forecastMaxInt, boolean plum, boolean warning) {
    }

    default String getReportDateTimeIso() {
        return REPORT_DATE_TIME_FORMAT.format(getReportDateTime());
    }

    default boolean isAccurateEnough() {
        return isEpicenterAccurateEnough() && isDepthAccurateEnough() && isMagnitudeAccurateEnough() && !Strings.CS.equals(getCondition(), "仮定震源要素");
    }

    default boolean isEpicenterAccurateEnough() {
        for (int rank : getEpicenterAccuracyRanks()) {
            if (rank == 0 || rank == 1)
                return false;
        }
        return true;
    }

    default boolean isDepthAccurateEnough() {
        return getDepthAccuracyRank() != 0 && getDepthAccuracyRank() != 1;
    }

    default boolean isMagnitudeAccurateEnough() {
        return !(getMagnitudeCalculationRank() == 0 || getNumberOfMagnitudeCalculation() == 1);
    }

    default boolean hasWarningUpdate() {
        if (!isEEWWarning())
            return false;
        for (WarningRegion region : getWarningRegions()) {
            if ("00".equals(region.lastKindCode()))
                return true;
        }
        return false;
    }

    // PLUM法の予測地域を最大震度ごとに全角空白区切りでまとめ、震度の高い順に並べる。createEmbedとtoExternalDtoで共用
    private static List<Map.Entry<ForecastMaxInt, String>> plumRegionsByMaxInt(List<ForecastRegion> regions) {
        return regions.stream()
                .filter(ForecastRegion::plum)
                .filter(region -> region.forecastMaxInt() != null)
                .collect(Collectors.groupingBy(ForecastRegion::forecastMaxInt,
                        Collectors.mapping(ForecastRegion::name, Collectors.joining("　"))))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> SeismicIntensity.get(entry.getKey().from()), Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    default List<PendingComponent> createComponents(String lang, ComponentContext ctx, Supplier<IComponentBuilder> factory) {
        IComponentBuilder builder = factory.get();
        if (isCancelReport()) {
            if (isConcurrent())
                builder.heading("eewbot.eew.eewcancel.concurrent", getConcurrentIndex());
            else
                builder.heading("eewbot.eew.eewcancel");
            return List.of(builder.timestamp(getReportDateTime())
                    .rawText(getText())
                    .accent(Color.YELLOW)
                    .footer(getPublishingOffice())
                    .build());
        }

        boolean eewWarning = isEEWWarning();
        if (eewWarning) {
            if (isLastInfo()) {
                if (isConcurrent())
                    builder.heading("eewbot.eew.eewalert.final.concurrent", getConcurrentIndex());
                else
                    builder.heading("eewbot.eew.eewalert.final");
            } else {
                if (isConcurrent())
                    builder.heading("eewbot.eew.eewalert.num.concurrent", getConcurrentIndex(), getSerial());
                else
                    builder.heading("eewbot.eew.eewalert.num", getSerial());
            }
            builder.accent(Color.RED);
        } else {
            if (isLastInfo()) {
                if (isConcurrent())
                    builder.heading("eewbot.eew.eewprediction.final.concurrent", getConcurrentIndex());
                else
                    builder.heading("eewbot.eew.eewprediction.final");
            } else {
                if (isConcurrent())
                    builder.heading("eewbot.eew.eewprediction.num.concurrent", getConcurrentIndex(), getSerial());
                else
                    builder.heading("eewbot.eew.eewprediction.num", getSerial());
            }
            builder.accent(Color.BLUE);
        }
        builder.timestamp(getReportDateTime());
        List<ForecastRegion> forecastRegions = getForecastRegions();
        ForecastMaxInt forecastMaxInt = getForecastMaxInt();
        if (!Strings.CS.equals(getCondition(), "仮定震源要素")) {
            builder.detail("eewbot.eew.epicenter", getHypocenterName());
            String depthCondition = getDepthCondition();
            if (depthCondition != null) {
                builder.detail("eewbot.eew.depth", depthCondition);
            } else {
                builder.detail("eewbot.eew.depth", "eewbot.eew.km", getDepthValue());
            }
            String magnitudeValue = getMagnitudeValue();
            if (magnitudeValue != null) {
                builder.detail("eewbot.eew.magnitude", magnitudeValue);
            }
            if (forecastMaxInt != null) {
                builder.separator().detail("eewbot.eew.forecastseismicintensity",
                        SeismicIntensity.get(forecastMaxInt.from()).getSimple());
            }
        } else if (forecastRegions != null) {
            if (forecastRegions.isEmpty()) {
                if (forecastMaxInt != null) {
                    builder.detail("eewbot.eew.plumseismicintensityplus", "eewbot.eew.near",
                            SeismicIntensity.get(forecastMaxInt.from()).getSimple(),
                            getHypocenterName());
                }
            } else {
                plumRegionsByMaxInt(forecastRegions)
                        .forEach(entry -> {
                            if (entry.getKey().to().equals("over")) {
                                builder.detail("eewbot.eew.plumseismicintensityplus",
                                        entry.getValue(), SeismicIntensity.get(entry.getKey().from()).getSimple());
                            } else {
                                builder.detail("eewbot.eew.plumseismicintensity",
                                        entry.getValue(), SeismicIntensity.get(entry.getKey().to()).getSimple());
                            }
                        });
            }
        }

        if (eewWarning) {
            builder.detail("eewbot.eew.warningtext", getWarningRegions().stream()
                    .map(WarningRegion::name)
                    .collect(Collectors.joining(" ")));
        }

        if (!isAccurateEnough()) {
            builder.text("eewbot.eew.inaccurate");
        }
        builder.footer(getPublishingOffice());
        return List.of(builder.build());
    }

    @Override
    default String getDataType() {
        return "eew";
    }

    @Override
    default Object toExternalDto() {
        String reportDateTime = getReportDateTimeIso();
        String serialNo = getSerial();
        boolean concurrent = isConcurrent();
        int concurrentIndex = getConcurrentIndex();

        String epicenter = null;
        String depth = null;
        String magnitude = null;
        String condition = null;

        if (hasEarthquake()) {
            condition = getCondition();
            epicenter = getHypocenterName();
            String depthCondition = getDepthCondition();
            String depthValue = getDepthValue();
            if (depthCondition != null) {
                depth = depthCondition;
            } else if (depthValue != null) {
                depth = depthValue + "km";
            }
            // JSON側はM不明時にvalue=nullをString.valueOfへ通しリテラル"null"を出力するため、NaN由来のnullもそのまま通して同じ出力にする
            magnitude = String.valueOf(getMagnitudeValue());
        }

        String maxIntensity = null;
        List<String> regions = new ArrayList<>();

        ForecastMaxInt forecastMaxInt = getForecastMaxInt();
        if (forecastMaxInt != null) {
            maxIntensity = SeismicIntensity.get(forecastMaxInt.from()).getSimple();
        }

        List<ForecastRegion> forecastRegions = getForecastRegions();
        if (forecastRegions != null) {
            regions = plumRegionsByMaxInt(forecastRegions).stream()
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }

        return new EEWExternalData(
                isEEWWarning(),
                isLastInfo(),
                isCancelReport(),
                serialNo,
                reportDateTime,
                epicenter,
                depth,
                magnitude,
                maxIntensity,
                regions,
                getText(),
                condition,
                concurrent,
                concurrentIndex
        );
    }
}
