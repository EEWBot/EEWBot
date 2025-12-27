package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Earthquake;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Hypocenter;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class VXSE53Impl extends JmxSeis implements VXSE53 {

    @Override
    public Optional<SeismicIntensity> getQuakeInfoMaxInt() {
        return Optional.ofNullable(getBody().getIntensity()).map(Intensity::getObservation).map(Intensity.IntensityDetail::getMaxInt);
    }

    private Earthquake getEarthquake() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return Objects.requireNonNull(getBody().getEarthquakes().get(0));
    }

    private Hypocenter getHypocenter() {
        return Objects.requireNonNull(getEarthquake().getHypocenter());
    }

    private Intensity.IntensityDetail getObservation() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        Intensity intensity = Objects.requireNonNull(getBody().getIntensity());
        return Objects.requireNonNull(intensity.getObservation());
    }

    private Comment getComments() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return Objects.requireNonNull(getBody().getComments());
    }

    @Override
    public Instant getOriginTime() {
        return getEarthquake().getOriginTime();
    }

    @Override
    public SeismicIntensity getMaxInt() {
        return getObservation().getMaxInt();
    }

    @Override
    public String getHypocenterName() {
        return getHypocenter().getArea().getName();
    }

    @Override
    public Optional<String> getHypocenterDetailedName() {
        return Optional.ofNullable(getHypocenter().getArea().getDetailedName());
    }

    @Override
    public Optional<String> getDepth() {
        return Optional.ofNullable(getHypocenter().getArea().getCoordinate().get(0).getDepth());
    }

    @Override
    public String getMagnitude() {
        return Objects.requireNonNull(getEarthquake().getMagnitude()).getMagnitude();
    }

    @Override
    public Optional<Comment.CommentForm> getForecastComment() {
        return Optional.ofNullable(getComments().getForecastComment());
    }

    @Override
    public Optional<Comment.CommentForm> getVarComment() {
        return Optional.ofNullable(getComments().getVarComment());
    }

    @Override
    public Optional<String> getFreeFormComment() {
        return Optional.ofNullable(getComments().getFreeFormComment());
    }

    @Override
    public Instant getTime() {
        return getOriginTime();
    }

    @Override
    public Coordinate getCoordinate() {
        return getHypocenter().getArea().getCoordinate().getFirst();
    }

    @Override
    public Intensity.IntensityDetail getIntensityDetail() {
        return getObservation();
    }
}
