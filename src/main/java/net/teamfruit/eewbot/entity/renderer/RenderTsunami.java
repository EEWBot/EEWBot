package net.teamfruit.eewbot.entity.renderer;

import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.TsunamiItem;

import java.time.Instant;
import java.util.List;

public interface RenderTsunami {

    Instant getTime();

    List<Coordinate> getCoordinates();

    List<TsunamiItem> getForecastItems();
}
