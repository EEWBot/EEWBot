package net.teamfruit.eewbot.entity.external;

import java.util.List;

public class QuakeInfoExternalData {

    private ControlData control;
    private HeadData head;
    private List<IntensityArea> intensityAreas;

    public QuakeInfoExternalData() {
    }

    public ControlData getControl() {
        return this.control;
    }

    public void setControl(ControlData control) {
        this.control = control;
    }

    public HeadData getHead() {
        return this.head;
    }

    public void setHead(HeadData head) {
        this.head = head;
    }

    public List<IntensityArea> getIntensityAreas() {
        return this.intensityAreas;
    }

    public void setIntensityAreas(List<IntensityArea> intensityAreas) {
        this.intensityAreas = intensityAreas;
    }

    @Override
    public String toString() {
        return "QuakeInfoExternalData{" +
                "control=" + this.control +
                ", head=" + this.head +
                ", intensityAreas=" + this.intensityAreas +
                '}';
    }

    public static class ControlData {
        private String title;
        private String dateTime;
        private String status;
        private String editorialOffice;
        private String publishingOffice;

        public ControlData() {
        }

        public String getTitle() {
            return this.title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDateTime() {
            return this.dateTime;
        }

        public void setDateTime(String dateTime) {
            this.dateTime = dateTime;
        }

        public String getStatus() {
            return this.status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getEditorialOffice() {
            return this.editorialOffice;
        }

        public void setEditorialOffice(String editorialOffice) {
            this.editorialOffice = editorialOffice;
        }

        public String getPublishingOffice() {
            return this.publishingOffice;
        }

        public void setPublishingOffice(String publishingOffice) {
            this.publishingOffice = publishingOffice;
        }

        @Override
        public String toString() {
            return "ControlData{" +
                    "title='" + this.title + '\'' +
                    ", dateTime='" + this.dateTime + '\'' +
                    ", status='" + this.status + '\'' +
                    ", editorialOffice='" + this.editorialOffice + '\'' +
                    ", publishingOffice='" + this.publishingOffice + '\'' +
                    '}';
        }
    }

    public static class HeadData {
        private String title;
        private String reportDateTime;
        private String targetDateTime;
        private long eventId;
        private String infoType;
        private String serial;
        private String infoKind;
        private String infoKindVersion;
        private HeadLineData headLine;

        public HeadData() {
        }

        public String getTitle() {
            return this.title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getReportDateTime() {
            return this.reportDateTime;
        }

        public void setReportDateTime(String reportDateTime) {
            this.reportDateTime = reportDateTime;
        }

        public String getTargetDateTime() {
            return this.targetDateTime;
        }

        public void setTargetDateTime(String targetDateTime) {
            this.targetDateTime = targetDateTime;
        }

        public long getEventId() {
            return this.eventId;
        }

        public void setEventId(long eventId) {
            this.eventId = eventId;
        }

        public String getInfoType() {
            return this.infoType;
        }

        public void setInfoType(String infoType) {
            this.infoType = infoType;
        }

        public String getSerial() {
            return this.serial;
        }

        public void setSerial(String serial) {
            this.serial = serial;
        }

        public String getInfoKind() {
            return this.infoKind;
        }

        public void setInfoKind(String infoKind) {
            this.infoKind = infoKind;
        }

        public String getInfoKindVersion() {
            return this.infoKindVersion;
        }

        public void setInfoKindVersion(String infoKindVersion) {
            this.infoKindVersion = infoKindVersion;
        }

        public HeadLineData getHeadLine() {
            return this.headLine;
        }

        public void setHeadLine(HeadLineData headLine) {
            this.headLine = headLine;
        }

        @Override
        public String toString() {
            return "HeadData{" +
                    "title='" + this.title + '\'' +
                    ", reportDateTime='" + this.reportDateTime + '\'' +
                    ", targetDateTime='" + this.targetDateTime + '\'' +
                    ", eventId=" + this.eventId +
                    ", infoType='" + this.infoType + '\'' +
                    ", serial='" + this.serial + '\'' +
                    ", infoKind='" + this.infoKind + '\'' +
                    ", infoKindVersion='" + this.infoKindVersion + '\'' +
                    ", headLine=" + this.headLine +
                    '}';
        }
    }

    public static class HeadLineData {
        private String text;

        public HeadLineData() {
        }

        public String getText() {
            return this.text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return "HeadLineData{" +
                    "text='" + this.text + '\'' +
                    '}';
        }
    }

    public static class IntensityArea {
        private String name;
        private String intensity;

        public IntensityArea() {
        }

        public IntensityArea(String name, String intensity) {
            this.name = name;
            this.intensity = intensity;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIntensity() {
            return this.intensity;
        }

        public void setIntensity(String intensity) {
            this.intensity = intensity;
        }

        @Override
        public String toString() {
            return "IntensityArea{" +
                    "name='" + this.name + '\'' +
                    ", intensity='" + this.intensity + '\'' +
                    '}';
        }
    }
}
