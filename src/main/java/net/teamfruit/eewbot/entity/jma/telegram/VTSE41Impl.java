package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Tsunami;
import net.teamfruit.eewbot.entity.jma.telegram.seis.TsunamiDetail;
import net.teamfruit.eewbot.entity.jma.telegram.seis.TsunamiItem;

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
    public Optional<Comment.CommentForm> getWarningComment() {
        if (isCancelReport())
            return Optional.empty();
        Comment comments = getBody().getComments();
        if (comments == null)
            return Optional.empty();
        return Optional.ofNullable(comments.getWarningComment());
    }

    @Override
    public Optional<String> getFreeFormComment() {
        if (isCancelReport())
            return Optional.empty();
        Comment comments = getBody().getComments();
        if (comments == null)
            return Optional.empty();
        return Optional.ofNullable(comments.getFreeFormComment());
    }
}
