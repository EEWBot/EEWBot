package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Earthquake;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Hypocenter;
import net.teamfruit.eewbot.entity.jma.telegram.seis.Intensity;
import net.teamfruit.eewbot.entity.jma.telegram.seis.JmxSeis;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class VXSE61Impl extends JmxSeis implements VXSE61 {

    // なんとかしたい
    private Optional<QuakeInfo> getVXSE53() {
        return EEWBot.instance.getQuakeInfoStore().getReport(getHead().getEventID(), JMAXmlType.VXSE53);
    }

    @Override
    public Instant getOriginTime() {
        return getEarthquake().getOriginTime();
    }

    @Override
    public String getHypocenterName() {
        return getHypocenter().getArea().getName();
    }

    @Override
    public Optional<String> getDepth() {
        return Optional.ofNullable(getHypocenter().getArea().getCoordinate().getLast().getDepth());
    }

    @Override
    public String getMagnitude() {
        return Objects.requireNonNull(getEarthquake().getMagnitude()).getMagnitude();
    }

    @Override
    public Optional<SeismicIntensity> getQuakeInfoMaxInt() {
        // なんとかしたい
        return EEWBot.instance.getQuakeInfoStore().getReport(getHead().getEventID(), JMAXmlType.VXSE53)
                .flatMap(QuakeInfo::getQuakeInfoMaxInt);
    }

    @Override
    public Intensity.IntensityDetail getIntensityDetail() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return getVXSE53().map(QuakeInfo::getIntensityDetail).orElse(null);
    }

    private Earthquake getEarthquake() {
        if (isCancelReport())
            throw new IllegalStateException("Cancel report");
        return Objects.requireNonNull(getBody().getEarthquakes().get(0));
    }

    private Hypocenter getHypocenter() {
        return Objects.requireNonNull(getEarthquake().getHypocenter());
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
