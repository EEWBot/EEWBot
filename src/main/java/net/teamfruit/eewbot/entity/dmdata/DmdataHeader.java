package net.teamfruit.eewbot.entity.dmdata;

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
        return this.originalId;
    }

    public Schema getSchema() {
        return this.schema;
    }

    public String getType() {
        return this.type;
    }

    public String getTitle() {
        return this.title;
    }

    public String getStatus() {
        return this.status;
    }

    public String getInfoType() {
        return this.infoType;
    }

    public String getEditorialOffice() {
        return this.editorialOffice;
    }

    public List<String> getPublishingOffice() {
        return this.publishingOffice;
    }

    public String getPressDateTime() {
        return this.pressDateTime;
    }

    public String getReportDateTime() {
        return this.reportDateTime;
    }

    public String getTargetDateTime() {
        return this.targetDateTime;
    }

    public String getEventId() {
        return this.eventId;
    }

    public String getSerialNo() {
        return this.serialNo;
    }

    public String getInfoKind() {
        return this.infoKind;
    }

    public String getInfoKindVersion() {
        return this.infoKindVersion;
    }

    public String getHeadline() {
        return this.headline;
    }

    public static class Schema {

        private String type;
        private String version;

        public String getType() {
            return this.type;
        }

        public String getVersion() {
            return this.version;
        }

        @Override
        public String toString() {
            return "Schema{" +
                    "type='" + this.type + '\'' +
                    ", version='" + this.version + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataHeader{" +
                "_originalId='" + this.originalId + '\'' +
                ", _schema=" + this.schema +
                ", type='" + this.type + '\'' +
                ", title='" + this.title + '\'' +
                ", status='" + this.status + '\'' +
                ", infoType='" + this.infoType + '\'' +
                ", editorialOffice='" + this.editorialOffice + '\'' +
                ", publishingOffice=" + this.publishingOffice +
                ", pressDateTime='" + this.pressDateTime + '\'' +
                ", reportDateTime='" + this.reportDateTime + '\'' +
                ", targetDateTime='" + this.targetDateTime + '\'' +
                ", eventId='" + this.eventId + '\'' +
                ", serialNo='" + this.serialNo + '\'' +
                ", infoKind='" + this.infoKind + '\'' +
                ", infoKindVersion='" + this.infoKindVersion + '\'' +
                ", headline='" + this.headline + '\'' +
                '}';
    }
}
