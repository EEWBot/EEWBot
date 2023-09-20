package net.teamfruit.eewbot.entity;

import com.google.gson.annotations.SerializedName;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DmdataHeader {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Tokyo"));

    @SerializedName("_originalId")
    private String originalId;
    @SerializedName("_schema")
    private Schema schema;
    private String type;
    private String title;
    private String status;
    private String infoType;
    private String editorialOffice;
    private List<String> publishingOffice;
    private String pressDateTime;
    private String reportDateTime;
    private String targetDateTime;
    private String eventId;
    private String serialNo;
    private String infoKind;
    private String infoKindVersion;
    private String headline;

    public String getOriginalId() {
        return originalId;
    }

    public Schema getSchema() {
        return schema;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getInfoType() {
        return infoType;
    }

    public String getEditorialOffice() {
        return editorialOffice;
    }

    public List<String> getPublishingOffice() {
        return publishingOffice;
    }

    public String getPressDateTime() {
        return pressDateTime;
    }

    public String getReportDateTime() {
        return reportDateTime;
    }

    public String getTargetDateTime() {
        return targetDateTime;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public String getInfoKind() {
        return infoKind;
    }

    public String getInfoKindVersion() {
        return infoKindVersion;
    }

    public String getHeadline() {
        return headline;
    }

    public static class Schema {

        private String type;
        private String version;

        public String getType() {
            return type;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "Schema{" +
                    "type='" + type + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataHeader{" +
                "_originalId='" + originalId + '\'' +
                ", _schema=" + schema +
                ", type='" + type + '\'' +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", infoType='" + infoType + '\'' +
                ", editorialOffice='" + editorialOffice + '\'' +
                ", publishingOffice=" + publishingOffice +
                ", pressDateTime='" + pressDateTime + '\'' +
                ", reportDateTime='" + reportDateTime + '\'' +
                ", targetDateTime='" + targetDateTime + '\'' +
                ", eventId='" + eventId + '\'' +
                ", serialNo='" + serialNo + '\'' +
                ", infoKind='" + infoKind + '\'' +
                ", infoKindVersion='" + infoKindVersion + '\'' +
                ", headline='" + headline + '\'' +
                '}';
    }
}
