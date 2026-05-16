package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.*;

import java.time.Instant;
import java.util.*;

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
        return getReportDateTime();
    }

    @Override
    public List<Coordinate> getCoordinates() {
        if (isCancelReport())
            return Collections.emptyList();

        List<Coordinate> result = new ArrayList<>();
        for (Earthquake earthquake : getBody().getEarthquakes()) {
            Hypocenter hypocenter = earthquake.getHypocenter();
            if (hypocenter == null)
                continue;

            List<Coordinate> coordinates = hypocenter.getArea().getCoordinate();
            if (!coordinates.isEmpty()) {
                result.add(coordinates.getFirst());
            }
        }
        return result;
    }
}
