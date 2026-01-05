package net.teamfruit.eewbot.entity.external;

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

        public QuakeInfoExternalData build() {
            return new QuakeInfoExternalData(this);
        }
    }
}
