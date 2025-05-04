package net.teamfruit.eewbot.entity.jma;

import net.teamfruit.eewbot.entity.Entity;

import java.time.Instant;

public interface JMAReport extends Entity {

    String getHeadTitle();

    Instant getDateTime();

    JMAStatus getStatus();

    String getEditorialOffice();

    String getPublishingOffice();

    Instant getReportDateTime();

    long getEventId();

    JMAInfoType getInfoType();

    String getSerial();

    String getInfoKind();

    String getInfoKindVersion();

    @SuppressWarnings("NonAsciiCharacters")
    default boolean isCancelReport() {
        return getInfoType() == JMAInfoType.取消;
    }
}
