package net.teamfruit.eewbot.entity.jma;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;

import java.util.Optional;

/** A JMA report about one earthquake event that carries (or references) observed intensity. */
public interface QuakeInfo extends JMAReport {

    Optional<SeismicIntensity> getQuakeInfoMaxInt();

    Intensity.IntensityDetail getIntensityDetail();

    default void initQuakeInfoStore(QuakeInfoStore store) {
        // no-op; overridden by types that need cross-report lookup (VXSE52Impl, VXSE61Impl)
    }

}
