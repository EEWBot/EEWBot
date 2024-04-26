package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.jma.telegram.common.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.telegram.common.JMAStatus;

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
