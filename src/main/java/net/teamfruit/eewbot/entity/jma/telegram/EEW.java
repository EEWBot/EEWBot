package net.teamfruit.eewbot.entity.jma.telegram;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.external.EEWExternalData;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.i18n.IEmbedBuilder;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    default boolean isCanceled() {
        return isCancelReport();
    }

    default String getSerialNo() {
        return getSerial();
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

    @Override
    default <T> T createEmbed(String lang, EmbedContext ctx, IEmbedBuilder<T> builder) {
        if (isCanceled()) {
            if (isConcurrent())
                builder.title("eewbot.eew.eewcancel.concurrent", getConcurrentIndex());
            else
                builder.title("eewbot.eew.eewcancel");
            return builder.timestamp(getReportDateTime())
                    .description(getText())
                    .color(Color.YELLOW)
                    .footer(getPublishingOffice(), null)
                    .build();
        }

        if (isEEWWarning()) {
            if (isLastInfo()) {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewalert.final.concurrent", getConcurrentIndex());
                else
                    builder.title("eewbot.eew.eewalert.final");
            } else {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewalert.num.concurrent", getConcurrentIndex(), getSerialNo());
                else
                    builder.title("eewbot.eew.eewalert.num", getSerialNo());
            }
            builder.color(Color.RED);
        } else {
            if (isLastInfo()) {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewprediction.final.concurrent", getConcurrentIndex());
                else
                    builder.title("eewbot.eew.eewprediction.final");
            } else {
                if (isConcurrent())
                    builder.title("eewbot.eew.eewprediction.num.concurrent", getConcurrentIndex(), getSerialNo());
                else
                    builder.title("eewbot.eew.eewprediction.num", getSerialNo());
            }
            builder.color(Color.BLUE);
        }
        builder.timestamp(getReportDateTime());
        List<ForecastRegion> forecastRegions = getForecastRegions();
        if (!Strings.CS.equals(getCondition(), "仮定震源要素")) {
            builder.addField("eewbot.eew.epicenter", getHypocenterName(), true);
            if (getDepthCondition() != null) {
                builder.addField("eewbot.eew.depth", getDepthCondition(), true);
            } else {
                builder.addField("eewbot.eew.depth", "eewbot.eew.km", true, getDepthValue());
            }
            // JSON側ではmagnitudeオブジェクトが常に存在しM不明時はvalueのみnullになるため、NaN時もフィールド自体は追加してJSON経路と同じ出力にする
            builder.addField("eewbot.eew.magnitude", getMagnitudeValue(), true);
            if (getForecastMaxInt() != null) {
                builder.addField("eewbot.eew.forecastseismicintensity",
                        SeismicIntensity.get(getForecastMaxInt().from()).getSimple(),
                        false);
            }
        } else if (forecastRegions != null) {
            if (forecastRegions.isEmpty()) {
                builder.addField("eewbot.eew.plumseismicintensityplus", "eewbot.eew.near", false,
                        SeismicIntensity.get(getForecastMaxInt().from()).getSimple(),
                        getHypocenterName());
            } else {
                forecastRegions.stream()
                        .filter(ForecastRegion::plum)
                        .collect(Collectors.groupingBy(ForecastRegion::forecastMaxInt,
                                Collectors.mapping(ForecastRegion::name, Collectors.joining("　"))))
                        .entrySet()
                        .stream()
                        .sorted(Comparator.comparing(entry -> SeismicIntensity.get(entry.getKey().from()), Comparator.reverseOrder()))
                        .forEach(entry -> {
                            if (entry.getKey().to().equals("over")) {
                                builder.addField("eewbot.eew.plumseismicintensityplus",
                                        entry.getValue(), false, SeismicIntensity.get(entry.getKey().from()).getSimple());
                            } else {
                                builder.addField("eewbot.eew.plumseismicintensity",
                                        entry.getValue(), false, SeismicIntensity.get(entry.getKey().to()).getSimple());
                            }
                        });
            }
        }

        if (isEEWWarning()) {
            builder.addField("eewbot.eew.warningtext", getWarningRegions().stream()
                    .map(WarningRegion::name)
                    .collect(Collectors.joining(" ")), false);
        }

        if (!isAccurateEnough()) {
            builder.description("eewbot.eew.inaccurate");
        }
        builder.footer(getPublishingOffice(), null);
        return builder.build();
    }

    @Override
    default String getDataType() {
        return "eew";
    }

    @Override
    default Object toExternalDto() {
        String reportDateTime = getReportDateTimeIso();
        String serialNo = getSerialNo();
        boolean concurrent = isConcurrent();
        int concurrentIndex = getConcurrentIndex();

        String epicenter = null;
        String depth = null;
        String magnitude = null;
        String condition = null;

        if (hasEarthquake()) {
            condition = getCondition();
            epicenter = getHypocenterName();
            if (getDepthCondition() != null) {
                depth = getDepthCondition();
            } else if (getDepthValue() != null) {
                depth = getDepthValue() + "km";
            }
            // JSON側はM不明時にvalue=nullをString.valueOfへ通しリテラル"null"を出力するため、NaN由来のnullもそのまま通して同じ出力にする
            magnitude = String.valueOf(getMagnitudeValue());
        }

        String maxIntensity = null;
        List<String> regions = new ArrayList<>();

        if (getForecastMaxInt() != null) {
            maxIntensity = SeismicIntensity.get(getForecastMaxInt().from()).getSimple();
        }

        List<ForecastRegion> forecastRegions = getForecastRegions();
        if (forecastRegions != null) {
            regions = forecastRegions.stream()
                    .filter(ForecastRegion::plum)
                    .collect(Collectors.groupingBy(ForecastRegion::forecastMaxInt,
                            Collectors.mapping(ForecastRegion::name, Collectors.joining("　"))))
                    .entrySet()
                    .stream()
                    .sorted(Comparator.comparing(entry -> SeismicIntensity.get(entry.getKey().from()), Comparator.reverseOrder()))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }

        return new EEWExternalData(
                isEEWWarning(),
                isLastInfo(),
                isCanceled(),
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
