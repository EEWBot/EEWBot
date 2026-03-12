package net.teamfruit.eewbot.entity.jma;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;

import java.util.Optional;

public interface QuakeInfo extends Entity {

    String getEventId();

    Optional<SeismicIntensity> getQuakeInfoMaxInt();

    Intensity.IntensityDetail getIntensityDetail();

    default void initQuakeInfoStore(QuakeInfoStore store) {
        // no-op; overridden by types that need cross-report lookup (VXSE52Impl, VXSE61Impl)
    }

}
