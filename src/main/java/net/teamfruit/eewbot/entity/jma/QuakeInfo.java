package net.teamfruit.eewbot.entity.jma;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A JMA report about one earthquake event that carries (or references) observed intensity.
 * <p>
 * VXSE51 and VXSE53 carry the intensity themselves. VXSE52 and VXSE61 carry none and resolve it
 * from the same event's VXSE51 / VXSE53 in {@code references}; without that report (or without a
 * store) they have no intensity.
 */
public interface QuakeInfo extends JMAReport {

    Optional<SeismicIntensity> getQuakeInfoMaxInt(@Nullable QuakeInfoStore references);

    Intensity.IntensityDetail getIntensityDetail(@Nullable QuakeInfoStore references);

}
