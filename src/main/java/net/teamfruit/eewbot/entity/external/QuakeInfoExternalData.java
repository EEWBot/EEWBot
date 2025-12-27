package net.teamfruit.eewbot.entity.external;

import java.util.List;

public class QuakeInfoExternalData {

    // Control相当の情報
    private String title;
    private long dateTime;
    private String status;
    private String editorialOffice;
    private String publishingOffice;

    // Head相当の情報
    private String headTitle;
    private long reportDateTime;
    private long eventId;
    private String infoType;
    private String serial;
    private String infoKind;
    private String infoKindVersion;

    // その他
    private List<IntensityArea> intensityAreas;

    private QuakeInfoExternalData(Builder builder) {
        this.title = builder.title;
        this.dateTime = builder.dateTime;
        this.status = builder.status;
        this.editorialOffice = builder.editorialOffice;
        this.publishingOffice = builder.publishingOffice;
        this.headTitle = builder.headTitle;
        this.reportDateTime = builder.reportDateTime;
        this.eventId = builder.eventId;
        this.infoType = builder.infoType;
        this.serial = builder.serial;
        this.infoKind = builder.infoKind;
        this.infoKindVersion = builder.infoKindVersion;
        this.intensityAreas = builder.intensityAreas;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTitle() {
        return this.title;
    }

    public long getDateTime() {
        return this.dateTime;
    }

    public String getStatus() {
        return this.status;
    }

    public String getEditorialOffice() {
        return this.editorialOffice;
    }

    public String getPublishingOffice() {
        return this.publishingOffice;
    }

    public String getHeadTitle() {
        return this.headTitle;
    }

    public long getReportDateTime() {
        return this.reportDateTime;
    }

    public long getEventId() {
        return this.eventId;
    }

    public String getInfoType() {
        return this.infoType;
    }

    public String getSerial() {
        return this.serial;
    }

    public String getInfoKind() {
        return this.infoKind;
    }

    public String getInfoKindVersion() {
        return this.infoKindVersion;
    }

    public List<IntensityArea> getIntensityAreas() {
        return this.intensityAreas;
    }

    @Override
    public String toString() {
        return "QuakeInfoExternalData{" +
                "title='" + this.title + '\'' +
                ", dateTime=" + this.dateTime +
                ", status='" + this.status + '\'' +
                ", editorialOffice='" + this.editorialOffice + '\'' +
                ", publishingOffice='" + this.publishingOffice + '\'' +
                ", headTitle='" + this.headTitle + '\'' +
                ", reportDateTime=" + this.reportDateTime +
                ", eventId=" + this.eventId +
                ", infoType='" + this.infoType + '\'' +
                ", serial='" + this.serial + '\'' +
                ", infoKind='" + this.infoKind + '\'' +
                ", infoKindVersion='" + this.infoKindVersion + '\'' +
                ", intensityAreas=" + this.intensityAreas +
                '}';
    }

    public static class Builder {
        private String title;
        private long dateTime;
        private String status;
        private String editorialOffice;
        private String publishingOffice;
        private String headTitle;
        private long reportDateTime;
        private long eventId;
        private String infoType;
        private String serial;
        private String infoKind;
        private String infoKindVersion;
        private List<IntensityArea> intensityAreas;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder dateTime(long dateTime) {
            this.dateTime = dateTime;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder editorialOffice(String editorialOffice) {
            this.editorialOffice = editorialOffice;
            return this;
        }

        public Builder publishingOffice(String publishingOffice) {
            this.publishingOffice = publishingOffice;
            return this;
        }

        public Builder headTitle(String headTitle) {
            this.headTitle = headTitle;
            return this;
        }

        public Builder reportDateTime(long reportDateTime) {
            this.reportDateTime = reportDateTime;
            return this;
        }

        public Builder eventId(long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder infoType(String infoType) {
            this.infoType = infoType;
            return this;
        }

        public Builder serial(String serial) {
            this.serial = serial;
            return this;
        }

        public Builder infoKind(String infoKind) {
            this.infoKind = infoKind;
            return this;
        }

        public Builder infoKindVersion(String infoKindVersion) {
            this.infoKindVersion = infoKindVersion;
            return this;
        }

        public Builder intensityAreas(List<IntensityArea> intensityAreas) {
            this.intensityAreas = intensityAreas;
            return this;
        }

        public QuakeInfoExternalData build() {
            return new QuakeInfoExternalData(this);
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
