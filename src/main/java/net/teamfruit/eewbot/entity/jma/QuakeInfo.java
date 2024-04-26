package net.teamfruit.eewbot.entity.jma;

import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.SeismicIntensity;

import java.util.Optional;

public interface QuakeInfo extends Entity {

    long getEventId();

    Optional<SeismicIntensity> getQuakeInfoMaxInt();

}
