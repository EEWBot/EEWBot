package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class VTSE41Impl extends JmxSeis implements VTSE41 {

    private Tsunami getTsunami() {
        return Objects.requireNonNull(getBody().getTsunami());
    }

    private TsunamiDetail getForecast() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return Objects.requireNonNull(getTsunami().getForecast());
    }

    @Override
    public List<TsunamiItem> getForecastItems() {
        if (isCancelReport())
            return Collections.emptyList();
        List<TsunamiItem> items = getForecast().getItems();
        return items != null ? items : Collections.emptyList();
    }

    @Override
    public Optional<String> getText() {
        return Optional.ofNullable(getBody().getText());
    }

    @Override
    public Optional<Comment.CommentForm> getWarningComment() {
        Comment comments = getBody().getComments();
        if (comments == null)
            return Optional.empty();
        return Optional.ofNullable(getBody().getComments().getWarningComment());
    }

    @Override
    public Optional<String> getFreeFormComment() {
        Comment comments = getBody().getComments();
        if (comments == null)
            return Optional.empty();
        return Optional.ofNullable(comments.getFreeFormComment());
    }

    @Override
    public Instant getTime() {
        return getHead().getTargetDateTime();
    }

    @Override
    public @Nullable Coordinate getCoordinate() {
        if (isCancelReport())
            return null;

        List<Earthquake> earthquakes = getBody().getEarthquakes();
        if (earthquakes.isEmpty())
            return null;

        Hypocenter hypocenter = earthquakes.getFirst().getHypocenter();
        if (hypocenter == null)
            return null;

        List<Coordinate> coordinates =  hypocenter.getArea().getCoordinate();
        if (coordinates.isEmpty())
            return null;

        return coordinates.getFirst();
    }
}
