package net.teamfruit.eewbot.entity.jma;

import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;

import java.util.Optional;

public interface QuakeInfo extends Entity {

    long getEventId();

    Optional<SeismicIntensity> getQuakeInfoMaxInt();

    Intensity.IntensityDetail getIntensityDetail();

}
