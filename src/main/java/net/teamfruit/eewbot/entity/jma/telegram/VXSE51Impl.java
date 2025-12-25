package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.telegram.common.Comment;
import net.teamfruit.eewbot.entity.jma.telegram.common.Coordinate;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.IntensityPref;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class VXSE51Impl extends JmxSeis implements VXSE51 {

    @Override
    public Optional<SeismicIntensity> getQuakeInfoMaxInt() {
        return Optional.ofNullable(getBody().getIntensity()).map(Intensity::getObservation).map(Intensity.IntensityDetail::getMaxInt);
    }

    @Override
    public Instant getTargetDateTime() {
        return getHead().getTargetDateTime();
    }

    private Intensity.IntensityDetail getObservation() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        Intensity intensity = Objects.requireNonNull(getBody().getIntensity());
        return Objects.requireNonNull(intensity.getObservation());
    }

    private Comment getComments() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return Objects.requireNonNull(getBody().getComments());
    }

    @Override
    public SeismicIntensity getMaxInt() {
        return getObservation().getMaxInt();
    }

    @Override
    public List<IntensityPref> getPrefs() {
        return getObservation().getIntensityPref();
    }

    @Override
    public Optional<Comment.CommentForm> getForecastComment() {
        return Optional.ofNullable(getComments().getForecastComment());
    }

    @Override
    public Optional<String> getFreeFormComment() {
        return Optional.ofNullable(getComments().getFreeFormComment());
    }

    @Override
    public Instant getTime() {
        return getTargetDateTime();
    }

    @Override
    @Nullable
    public Coordinate getCoordinate() {
        return null;
    }

    @Override
    public Intensity.IntensityDetail getIntensityDetail() {
        return getObservation();
    }

    @Override
    public Object toExternalDto() {
        QuakeInfoExternalData data = new QuakeInfoExternalData();

        // Populate ControlData from control
        if (this.control != null) {
            QuakeInfoExternalData.ControlData controlData = new QuakeInfoExternalData.ControlData();
            controlData.setTitle(this.control.getTitle());
            if (this.control.getDateTime() != null) {
                controlData.setDateTime(this.control.getDateTime().toString());
            }
            if (this.control.getStatus() != null) {
                controlData.setStatus(this.control.getStatus().toString());
            }
            controlData.setEditorialOffice(this.control.getEditorialOffice());
            controlData.setPublishingOffice(this.control.getPublishingOffice());
            data.setControl(controlData);
        }

        // Populate HeadData from head
        if (this.head != null) {
            QuakeInfoExternalData.HeadData headData = new QuakeInfoExternalData.HeadData();
            headData.setTitle(this.head.getTitle());
            if (this.head.getReportDateTime() != null) {
                headData.setReportDateTime(this.head.getReportDateTime()
                        .atZone(java.time.ZoneId.of("Asia/Tokyo"))
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            if (this.head.getTargetDateTime() != null) {
                headData.setTargetDateTime(this.head.getTargetDateTime()
                        .atZone(java.time.ZoneId.of("Asia/Tokyo"))
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            headData.setEventId(this.head.getEventID());
            if (this.head.getInfoType() != null) {
                headData.setInfoType(this.head.getInfoType().toString());
            }
            headData.setSerial(this.head.getSerial());
            headData.setInfoKind(this.head.getInfoKind());
            headData.setInfoKindVersion(this.head.getInfoKindVersion());

            // Extract headline text if present
            if (this.head.getHeadline() != null) {
                QuakeInfoExternalData.HeadLineData headLine = new QuakeInfoExternalData.HeadLineData();
                headLine.setText(this.head.getHeadline().getText());
                headData.setHeadLine(headLine);
            }

            data.setHead(headData);
        }

        // Initialize empty intensityAreas list
        data.setIntensityAreas(new java.util.ArrayList<>());

        return data;
    }
}
