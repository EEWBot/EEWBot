package net.teamfruit.eewbot.entity.renderer;

import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import reactor.util.annotation.Nullable;

import java.time.Instant;

public interface RenderQuakePrefecture {

    Instant getTime();

    @Nullable
    Coordinate getCoordinate();

    Intensity.IntensityDetail getIntensityDetail();
}
