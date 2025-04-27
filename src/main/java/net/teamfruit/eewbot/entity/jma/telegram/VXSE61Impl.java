package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Earthquake;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Hypocenter;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class VXSE61Impl extends JmxSeis implements VXSE61 {

    // なんとかしたい
    private Optional<QuakeInfo> getVXSE51() {
        return EEWBot.instance.getQuakeInfoStore().getReport(getHead().getEventID(), JMAXmlType.VXSE51);
    }

    @Override
    public Instant getOriginTime() {
        return getEarthquake().getOriginTime();
    }

    @Override
    public String getHypocenterName() {
        return getHypocenter().getArea().getName();
    }

    @Override
    public Optional<String> getDepth() {
        return Optional.ofNullable(getHypocenter().getArea().getCoordinate().getLast().getDepth());
    }

    @Override
    public String getMagnitude() {
        return Objects.requireNonNull(getEarthquake().getMagnitude()).getMagnitude();
    }

    @Override
    public Optional<SeismicIntensity> getQuakeInfoMaxInt() {
        // なんとかしたい
        return EEWBot.instance.getQuakeInfoStore().getReport(getHead().getEventID(), JMAXmlType.VXSE53)
                .flatMap(QuakeInfo::getQuakeInfoMaxInt);
    }

    @Override
    public Intensity.IntensityDetail getIntensityDetail() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return getVXSE51().map(QuakeInfo::getIntensityDetail).orElse(null);
    }

    private Earthquake getEarthquake() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return Objects.requireNonNull(getBody().getEarthquakes().get(0));
    }

    private Hypocenter getHypocenter() {
        return Objects.requireNonNull(getEarthquake().getHypocenter());
    }

}
