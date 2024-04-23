package net.teamfruit.eewbot.entity.jma;

import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.util.Optional;

public interface QuakeInfo extends IJMAReport {

    Optional<SeismicIntensity> getMaxInt();
}
