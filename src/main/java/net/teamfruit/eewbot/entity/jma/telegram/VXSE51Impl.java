package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.IntensityPref;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class VXSE51Impl extends JmxSeis implements VXSE51 {

    @Override
    public Optional<SeismicIntensity> getQuakeInfoMaxInt() {
        return Optional.ofNullable(getBody().getIntensity()).map(Intensity::getObservation).map(Intensity.IntensityDetail::getMaxInt);
    }

    @Override
    public Instant getTargetDateTime() {
        return getHead().getTargetDateTime();
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
    public SeismicIntensity getMaxInt() {
        return getObservation().getMaxInt();
    }

    @Override
    public List<IntensityPref> getPrefs() {
        return getObservation().getIntensityPref();
    }

    @Override
    public Optional<Comment.CommentForm> getForecastComment() {
        return Optional.ofNullable(getComments().getForecastComment());
    }

    @Override
    public Optional<String> getFreeFormComment() {
        return Optional.ofNullable(getComments().getFreeFormComment());
    }

}
