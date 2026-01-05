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
    private long reportDateTime;
    private long eventId;
    private String infoType;
    private String serial;

    // 震度情報（VXSE51, VXSE53のみ）
    private String maxInt;
    private List<IntensityAreaInfo> intensities;

    // 震源情報（VXSE52, VXSE53, VXSE61のみ）
    private Long originTime;
    private String hypocenterName;
    private String hypocenterDetailedName;
    private Float latitude;
    private Float longitude;
    private String depth;
    private String magnitude;

    // コメント情報
    private String forecastComment;
    private String freeFormComment;

    private QuakeInfoExternalData(Builder builder) {
        this.title = builder.title;
        this.dateTime = builder.dateTime;
        this.status = builder.status;
        this.editorialOffice = builder.editorialOffice;
        this.publishingOffice = builder.publishingOffice;
        this.reportDateTime = builder.reportDateTime;
        this.eventId = builder.eventId;
        this.infoType = builder.infoType;
        this.serial = builder.serial;
        this.maxInt = builder.maxInt;
        this.intensities = builder.intensities;
        this.originTime = builder.originTime;
        this.hypocenterName = builder.hypocenterName;
        this.hypocenterDetailedName = builder.hypocenterDetailedName;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.depth = builder.depth;
        this.magnitude = builder.magnitude;
        this.forecastComment = builder.forecastComment;
        this.freeFormComment = builder.freeFormComment;
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

    public String getMaxInt() {
        return this.maxInt;
    }

    public List<IntensityAreaInfo> getIntensities() {
        return this.intensities;
    }

    public Long getOriginTime() {
        return this.originTime;
    }

    public String getHypocenterName() {
        return this.hypocenterName;
    }

    public String getHypocenterDetailedName() {
        return this.hypocenterDetailedName;
    }

    public Float getLatitude() {
        return this.latitude;
    }

    public Float getLongitude() {
        return this.longitude;
    }

    public String getDepth() {
        return this.depth;
    }

    public String getMagnitude() {
        return this.magnitude;
    }

    public String getForecastComment() {
        return this.forecastComment;
    }

    public String getFreeFormComment() {
        return this.freeFormComment;
    }

    @Override
    public String toString() {
        return "QuakeInfoExternalData{" +
                "title='" + this.title + '\'' +
                ", dateTime=" + this.dateTime +
                ", status='" + this.status + '\'' +
                ", editorialOffice='" + this.editorialOffice + '\'' +
                ", publishingOffice='" + this.publishingOffice + '\'' +
                ", reportDateTime=" + this.reportDateTime +
                ", eventId=" + this.eventId +
                ", infoType='" + this.infoType + '\'' +
                ", serial='" + this.serial + '\'' +
                ", maxInt='" + this.maxInt + '\'' +
                ", intensities=" + this.intensities +
                ", originTime=" + this.originTime +
                ", hypocenterName='" + this.hypocenterName + '\'' +
                ", hypocenterDetailedName='" + this.hypocenterDetailedName + '\'' +
                ", latitude=" + this.latitude +
                ", longitude=" + this.longitude +
                ", depth='" + this.depth + '\'' +
                ", magnitude='" + this.magnitude + '\'' +
                ", forecastComment='" + this.forecastComment + '\'' +
                ", freeFormComment='" + this.freeFormComment + '\'' +
                '}';
    }

    public static class Builder {
        private String title;
        private long dateTime;
        private String status;
        private String editorialOffice;
        private String publishingOffice;
        private long reportDateTime;
        private long eventId;
        private String infoType;
        private String serial;
        private String maxInt;
        private List<IntensityAreaInfo> intensities;
        private Long originTime;
        private String hypocenterName;
        private String hypocenterDetailedName;
        private Float latitude;
        private Float longitude;
        private String depth;
        private String magnitude;
        private String forecastComment;
        private String freeFormComment;

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

        public Builder maxInt(String maxInt) {
            this.maxInt = maxInt;
            return this;
        }

        public Builder intensities(List<IntensityAreaInfo> intensities) {
            this.intensities = intensities;
            return this;
        }

        public Builder originTime(Long originTime) {
            this.originTime = originTime;
            return this;
        }

        public Builder hypocenterName(String hypocenterName) {
            this.hypocenterName = hypocenterName;
            return this;
        }

        public Builder hypocenterDetailedName(String hypocenterDetailedName) {
            this.hypocenterDetailedName = hypocenterDetailedName;
            return this;
        }

        public Builder latitude(Float latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(Float longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder depth(String depth) {
            this.depth = depth;
            return this;
        }

        public Builder magnitude(String magnitude) {
            this.magnitude = magnitude;
            return this;
        }

        public Builder forecastComment(String forecastComment) {
            this.forecastComment = forecastComment;
            return this;
        }

        public Builder freeFormComment(String freeFormComment) {
            this.freeFormComment = freeFormComment;
            return this;
        }

        public QuakeInfoExternalData build() {
            return new QuakeInfoExternalData(this);
        }
    }

    public static class IntensityAreaInfo {
        private String prefName;
        private String prefCode;
        private String areaName;
        private String areaCode;
        private String maxInt;

        private IntensityAreaInfo(Builder builder) {
            this.prefName = builder.prefName;
            this.prefCode = builder.prefCode;
            this.areaName = builder.areaName;
            this.areaCode = builder.areaCode;
            this.maxInt = builder.maxInt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getPrefName() {
            return this.prefName;
        }

        public String getPrefCode() {
            return this.prefCode;
        }

        public String getAreaName() {
            return this.areaName;
        }

        public String getAreaCode() {
            return this.areaCode;
        }

        public String getMaxInt() {
            return this.maxInt;
        }

        @Override
        public String toString() {
            return "IntensityAreaInfo{" +
                    "prefName='" + this.prefName + '\'' +
                    ", prefCode='" + this.prefCode + '\'' +
                    ", areaName='" + this.areaName + '\'' +
                    ", areaCode='" + this.areaCode + '\'' +
                    ", maxInt='" + this.maxInt + '\'' +
                    '}';
        }

        public static class Builder {
            private String prefName;
            private String prefCode;
            private String areaName;
            private String areaCode;
            private String maxInt;

            public Builder prefName(String prefName) {
                this.prefName = prefName;
                return this;
            }

            public Builder prefCode(String prefCode) {
                this.prefCode = prefCode;
                return this;
            }

            public Builder areaName(String areaName) {
                this.areaName = areaName;
                return this;
            }

            public Builder areaCode(String areaCode) {
                this.areaCode = areaCode;
                return this;
            }

            public Builder maxInt(String maxInt) {
                this.maxInt = maxInt;
                return this;
            }

            public IntensityAreaInfo build() {
                return new IntensityAreaInfo(this);
            }
        }
    }
}
