package net.teamfruit.eewbot.entity.external;

import java.util.List;

public class TsunamiExternalData {

    private String title;
    private long dateTime;
    private String status;
    private String editorialOffice;
    private String publishingOffice;

    private long reportDateTime;
    private long eventId;
    private String infoType;
    private String serial;

    private List<ForecastAreaInfo> forecastAreas;

    private String warningComment;
    private String freeFormComment;

    private TsunamiExternalData(Builder builder) {
        this.title = builder.title;
        this.dateTime = builder.dateTime;
        this.status = builder.status;
        this.editorialOffice = builder.editorialOffice;
        this.publishingOffice = builder.publishingOffice;
        this.reportDateTime = builder.reportDateTime;
        this.eventId = builder.eventId;
        this.infoType = builder.infoType;
        this.serial = builder.serial;
        this.forecastAreas = builder.forecastAreas;
        this.warningComment = builder.warningComment;
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

    public List<ForecastAreaInfo> getForecastAreas() {
        return this.forecastAreas;
    }

    public String getWarningComment() {
        return this.warningComment;
    }

    public String getFreeFormComment() {
        return this.freeFormComment;
    }

    @Override
    public String toString() {
        return "TsunamiExternalData{" +
                "title='" + this.title + '\'' +
                ", dateTime=" + this.dateTime +
                ", status='" + this.status + '\'' +
                ", editorialOffice='" + this.editorialOffice + '\'' +
                ", publishingOffice='" + this.publishingOffice + '\'' +
                ", reportDateTime=" + this.reportDateTime +
                ", eventId=" + this.eventId +
                ", infoType='" + this.infoType + '\'' +
                ", serial='" + this.serial + '\'' +
                ", forecastAreas=" + this.forecastAreas +
                ", warningComment='" + this.warningComment + '\'' +
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
        private List<ForecastAreaInfo> forecastAreas;
        private String warningComment;
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

        public Builder forecastAreas(List<ForecastAreaInfo> forecastAreas) {
            this.forecastAreas = forecastAreas;
            return this;
        }

        public Builder warningComment(String warningComment) {
            this.warningComment = warningComment;
            return this;
        }

        public Builder freeFormComment(String freeFormComment) {
            this.freeFormComment = freeFormComment;
            return this;
        }

        public TsunamiExternalData build() {
            return new TsunamiExternalData(this);
        }
    }

    public static class ForecastAreaInfo {
        private String areaName;
        private String areaCode;
        private String categoryName;
        private String categoryCode;
        private String maxHeightDescription;
        private Long arrivalTime;

        private ForecastAreaInfo(Builder builder) {
            this.areaName = builder.areaName;
            this.areaCode = builder.areaCode;
            this.categoryName = builder.categoryName;
            this.categoryCode = builder.categoryCode;
            this.maxHeightDescription = builder.maxHeightDescription;
            this.arrivalTime = builder.arrivalTime;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getAreaName() {
            return this.areaName;
        }

        public String getAreaCode() {
            return this.areaCode;
        }

        public String getCategoryName() {
            return this.categoryName;
        }

        public String getCategoryCode() {
            return this.categoryCode;
        }

        public String getMaxHeightDescription() {
            return this.maxHeightDescription;
        }

        public Long getArrivalTime() {
            return this.arrivalTime;
        }

        @Override
        public String toString() {
            return "ForecastAreaInfo{" +
                    "areaName='" + this.areaName + '\'' +
                    ", areaCode='" + this.areaCode + '\'' +
                    ", categoryName='" + this.categoryName + '\'' +
                    ", categoryCode='" + this.categoryCode + '\'' +
                    ", maxHeightDescription='" + this.maxHeightDescription + '\'' +
                    ", arrivalTime=" + this.arrivalTime +
                    '}';
        }

        public static class Builder {
            private String areaName;
            private String areaCode;
            private String categoryName;
            private String categoryCode;
            private String maxHeightDescription;
            private Long arrivalTime;

            public Builder areaName(String areaName) {
                this.areaName = areaName;
                return this;
            }

            public Builder areaCode(String areaCode) {
                this.areaCode = areaCode;
                return this;
            }

            public Builder categoryName(String categoryName) {
                this.categoryName = categoryName;
                return this;
            }

            public Builder categoryCode(String categoryCode) {
                this.categoryCode = categoryCode;
                return this;
            }

            public Builder maxHeightDescription(String maxHeightDescription) {
                this.maxHeightDescription = maxHeightDescription;
                return this;
            }

            public Builder arrivalTime(Long arrivalTime) {
                this.arrivalTime = arrivalTime;
                return this;
            }

            public ForecastAreaInfo build() {
                return new ForecastAreaInfo(this);
            }
        }
    }
}
