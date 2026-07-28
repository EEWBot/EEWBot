package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractEEW extends JmxSeis implements EEW {

    private static final String EEW_INFORMATION_TYPE_PREFIX = "緊急地震速報";
    private static final String EEW_REGION_INFORMATION_TYPE = "緊急地震速報（細分区域）";
    // 09=予報(PLUM法), 19=警報(PLUM法)
    private static final Set<String> PLUM_KIND_CODES = Set.of("09", "19");
    // 10=警報(主要動未到達), 11=警報(既に主要動到達と推測), 19=警報(PLUM法)
    private static final Set<String> WARNING_KIND_CODES = Set.of("10", "11", "19");

    private boolean concurrent;
    private int concurrentIndex;

    // XMLグラフはパース後不変のため、走査を伴う派生値は初回アクセス時に計算して保持する
    private @Nullable Boolean eewWarning;
    private @Nullable List<WarningRegion> warningRegions;
    private boolean forecastMaxIntComputed;
    private @Nullable ForecastMaxInt forecastMaxInt;
    private boolean forecastRegionsComputed;
    private @Nullable List<ForecastRegion> forecastRegions;

    @Override
    public boolean isConcurrent() {
        return this.concurrent;
    }

    @Override
    public void setConcurrent(boolean concurrent) {
        this.concurrent = concurrent;
    }

    @Override
    public int getConcurrentIndex() {
        return this.concurrentIndex;
    }

    @Override
    public void setConcurrentIndex(int concurrentIndex) {
        this.concurrentIndex = concurrentIndex;
    }

    @Override
    public boolean isLastInfo() {
        // dmdata JSONのisLastInfoは「最終報・取消報の場合はtrue」。取消報のXMLにはNextAdvisoryが出現しないため、取消も最終扱いにしてJSON経路と同じ出力にする
        return isCancelReport() || getBody().getNextAdvisory() != null;
    }

    @Override
    public boolean isEEWWarning() {
        if (this.eewWarning == null) {
            this.eewWarning = computeEEWWarning();
        }
        return this.eewWarning;
    }

    private boolean computeEEWWarning() {
        if (isCancelReport())
            return false;
        List<Head.Headline.Information> informations = getHead().getHeadline().getInformations();
        if (informations == null)
            return false;
        return informations.stream().anyMatch(information -> information.getType() != null
                && information.getType().startsWith(EEW_INFORMATION_TYPE_PREFIX));
    }

    @Override
    public List<WarningRegion> getWarningRegions() {
        if (this.warningRegions == null) {
            this.warningRegions = computeWarningRegions();
        }
        return this.warningRegions;
    }

    private List<WarningRegion> computeWarningRegions() {
        List<Head.Headline.Information> informations = getHead().getHeadline().getInformations();
        if (informations == null)
            return Collections.emptyList();
        List<WarningRegion> regions = new ArrayList<>();
        for (Head.Headline.Information information : informations) {
            if (!EEW_REGION_INFORMATION_TYPE.equals(information.getType()) || information.getItems() == null)
                continue;
            for (Head.Headline.Item item : information.getItems()) {
                if (item.getAreas() == null || item.getAreas().getAreas() == null)
                    continue;
                String kindCode = item.getKind() != null ? item.getKind().getCode() : null;
                String lastKindCode = item.getLastKind() != null ? item.getLastKind().getCode() : null;
                for (Head.Headline.Area area : item.getAreas().getAreas()) {
                    regions.add(new WarningRegion(area.getName(), area.getCode(), kindCode, lastKindCode));
                }
            }
        }
        return regions;
    }

    @Override
    public boolean hasEarthquake() {
        return getBody().getEarthquakes() != null && !getBody().getEarthquakes().isEmpty();
    }

    @Nullable
    private Earthquake getEarthquake() {
        return hasEarthquake() ? getBody().getEarthquakes().getFirst() : null;
    }

    @Nullable
    private Hypocenter getHypocenter() {
        Earthquake earthquake = getEarthquake();
        return earthquake != null ? earthquake.getHypocenter() : null;
    }

    @Nullable
    private Coordinate getCoordinate() {
        Hypocenter hypocenter = getHypocenter();
        if (hypocenter == null)
            return null;
        List<Coordinate> coordinates = hypocenter.getArea().getCoordinate();
        return coordinates != null && !coordinates.isEmpty() ? coordinates.getFirst() : null;
    }

    private Accuracy getAccuracy() {
        return Objects.requireNonNull(Objects.requireNonNull(getHypocenter()).getAccuracy());
    }

    @Override
    @Nullable
    public String getCondition() {
        Earthquake earthquake = getEarthquake();
        return earthquake != null ? earthquake.getCondition() : null;
    }

    @Override
    @Nullable
    public String getHypocenterName() {
        Hypocenter hypocenter = getHypocenter();
        return hypocenter != null ? hypocenter.getArea().getName() : null;
    }

    @Override
    @Nullable
    public String getDepthValue() {
        Coordinate coordinate = getCoordinate();
        return coordinate != null ? coordinate.getDepthValue() : null;
    }

    @Override
    @Nullable
    public String getDepthCondition() {
        Coordinate coordinate = getCoordinate();
        return coordinate != null ? coordinate.getDepthCondition() : null;
    }

    @Override
    @Nullable
    public String getMagnitudeValue() {
        Earthquake earthquake = getEarthquake();
        if (earthquake == null)
            return null;
        Magnitude magnitude = earthquake.getMagnitude();
        if (magnitude == null)
            return null;
        // M不明は@condition="不明"のNaNで届く。JSON側のvalue=nullと同じ扱いになるようnullを返す
        float value = magnitude.getRawValue();
        return Float.isNaN(value) ? null : String.valueOf(value);
    }

    @Override
    public List<Integer> getEpicenterAccuracyRanks() {
        Accuracy.AccuracyEpicenter epicenter = getAccuracy().getEpicenter();
        return List.of(epicenter.getRank(), epicenter.getRank2());
    }

    @Override
    public int getDepthAccuracyRank() {
        return getAccuracy().getDepth().getRank();
    }

    @Override
    public int getMagnitudeCalculationRank() {
        return getAccuracy().getMagnitudeCalculation().getRank();
    }

    @Override
    public int getNumberOfMagnitudeCalculation() {
        return getAccuracy().getNumberOfMagnitudeCalculation();
    }

    @Nullable
    private Intensity.IntensityDetail getForecast() {
        Intensity intensity = getBody().getIntensity();
        return intensity != null ? intensity.getForecast() : null;
    }

    @Override
    @Nullable
    public ForecastMaxInt getForecastMaxInt() {
        if (!this.forecastMaxIntComputed) {
            this.forecastMaxInt = computeForecastMaxInt();
            this.forecastMaxIntComputed = true;
        }
        return this.forecastMaxInt;
    }

    @Nullable
    private ForecastMaxInt computeForecastMaxInt() {
        Intensity.IntensityDetail forecast = getForecast();
        if (forecast == null || forecast.getForecastInt() == null)
            return null;
        return new ForecastMaxInt(forecast.getForecastInt().getFrom(), forecast.getForecastInt().getTo());
    }

    @Override
    @Nullable
    public List<ForecastRegion> getForecastRegions() {
        if (!this.forecastRegionsComputed) {
            this.forecastRegions = computeForecastRegions();
            this.forecastRegionsComputed = true;
        }
        return this.forecastRegions;
    }

    @Nullable
    private List<ForecastRegion> computeForecastRegions() {
        Intensity.IntensityDetail forecast = getForecast();
        if (forecast == null)
            return null;
        List<IntensityPref> prefs = forecast.getIntensityPref();
        if (prefs == null)
            return Collections.emptyList();
        List<ForecastRegion> regions = new ArrayList<>();
        for (IntensityPref pref : prefs) {
            if (pref.getAreas() == null)
                continue;
            for (IntensityArea area : pref.getAreas()) {
                String kindCode = area.getCategory() != null ? area.getCategory().getKind().getCode() : null;
                ForecastInt forecastInt = area.getForecastInt();
                ForecastMaxInt maxInt = forecastInt != null
                        ? new ForecastMaxInt(forecastInt.getFrom(), forecastInt.getTo())
                        : null;
                regions.add(new ForecastRegion(area.getName(), maxInt,
                        kindCode != null && PLUM_KIND_CODES.contains(kindCode),
                        kindCode != null && WARNING_KIND_CODES.contains(kindCode)));
            }
        }
        return regions;
    }

    @Override
    @Nullable
    public String getText() {
        return getBody().getText();
    }
}
