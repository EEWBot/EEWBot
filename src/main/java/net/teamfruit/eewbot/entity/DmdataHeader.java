package net.teamfruit.eewbot.entity;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DmdataHeader {

    @SerializedName("_originalId")
    public String originalId;
    @SerializedName("_schema")
    public Schema schema;
    public String type;
    public String title;
    public String status;
    public String infoType;
    public String editorialOffice;
    public List<String> publishingOffice;
    public String pressDateTime;
    public String reportDateTime;
    public String targetDateTime;
    public String eventId;
    public String serialNo;
    public String infoKind;
    public String infoKindVersion;
    public String headline;

    public static class Schema {
        
        public String type;
        public String version;

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
