package net.teamfruit.eewbot.entity.jma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import net.teamfruit.eewbot.entity.Entity;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import reactor.util.annotation.Nullable;

import java.time.Instant;

@SuppressWarnings("unused")
@JacksonXmlRootElement(localName = "Report")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractJMAReport implements Entity, JMAReport, ExternalData {

    @JacksonXmlProperty(localName = "Control")
    protected Control control;

    @JacksonXmlProperty(localName = "Head")
    protected Head head;
    
    protected String rawData;

    public Control getControl() {
        return this.control;
    }

    public Head getHead() {
        return this.head;
    }

    public String getRawData() {
        return this.rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    @Override
    public String getHeadTitle() {
        return getHead().getTitle();
    }

    @Override
    public Instant getDateTime() {
        return getControl().getDateTime();
    }

    @Override
    public JMAStatus getStatus() {
        return getControl().getStatus();
    }

    @Override
    public String getEditorialOffice() {
        return getControl().getEditorialOffice();
    }

    @Override
    public String getPublishingOffice() {
        return getControl().getPublishingOffice();
    }

    @Override
    public Instant getReportDateTime() {
        return getHead().getReportDateTime();
    }

    @Override
    public long getEventId() {
        return getHead().getEventID();
    }

    @Override
    public JMAInfoType getInfoType() {
        return getHead().getInfoType();
    }

    @Override
    public String getSerial() {
        return getHead().getSerial();
    }

    @Override
    public String getInfoKind() {
        return getHead().getInfoKind();
    }

    @Override
    public String getInfoKindVersion() {
        return getHead().getInfoKindVersion();
    }

    public static class Control {

        @JacksonXmlProperty(localName = "Title")
        private String title;

        @JacksonXmlProperty(localName = "DateTime")
        private Instant dateTime;

        @JacksonXmlProperty(localName = "Status")
        private JMAStatus status;

        @JacksonXmlProperty(localName = "EditorialOffice")
        private String editorialOffice;

        @JacksonXmlProperty(localName = "PublishingOffice")
        private String publishingOffice;

        public String getTitle() {
            return this.title;
        }

        public Instant getDateTime() {
            return this.dateTime;
        }

        public JMAStatus getStatus() {
            return this.status;
        }

        public String getEditorialOffice() {
            return this.editorialOffice;
        }

        public String getPublishingOffice() {
            return this.publishingOffice;
        }

        @Override
        public String toString() {
            return "Control{" +
                    "title='" + this.title + '\'' +
                    ", dateTime='" + this.dateTime + '\'' +
                    ", status=" + this.status +
                    ", editorialOffice='" + this.editorialOffice + '\'' +
                    ", publishingOffice='" + this.publishingOffice + '\'' +
                    '}';
        }
    }

    public static class Head {

        @JacksonXmlProperty(localName = "Title")
        private String title;

        @JacksonXmlProperty(localName = "ReportDateTime")
        private Instant reportDateTime;

        @JacksonXmlProperty(localName = "TargetDateTime")
        private @Nullable Instant targetDateTime;

        @JacksonXmlProperty(localName = "TargetDTDubious")
        private @Nullable String targetDTDubious;

        @JacksonXmlProperty(localName = "TargetDuration")
        private @Nullable String targetDuration;

        @JacksonXmlProperty(localName = "ValidDateTime")
        private @Nullable String validDateTime;

        @JacksonXmlProperty(localName = "EventID")
        private long eventID;

        @JacksonXmlProperty(localName = "InfoType")
        private JMAInfoType infoType;

        @JacksonXmlProperty(localName = "Serial")
        private String serial;

        @JacksonXmlProperty(localName = "InfoKind")
        private String infoKind;

        @JacksonXmlProperty(localName = "InfoKindVersion")
        private String infoKindVersion;

        @JacksonXmlProperty(localName = "Headline")
        private Headline headline;

        public String getTitle() {
            return this.title;
        }

        public Instant getReportDateTime() {
            return this.reportDateTime;
        }

        @Nullable
        public Instant getTargetDateTime() {
            return this.targetDateTime;
        }

        @Nullable
        public String getTargetDTDubious() {
            return this.targetDTDubious;
        }

        @Nullable
        public String getTargetDuration() {
            return this.targetDuration;
        }

        @Nullable
        public String getValidDateTime() {
            return this.validDateTime;
        }

        public long getEventID() {
            return this.eventID;
        }

        public JMAInfoType getInfoType() {
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

        public Headline getHeadline() {
            return this.headline;
        }

        @JsonIgnoreProperties("Information")
        public static class Headline {

            @JacksonXmlProperty(localName = "Text")
            private String text;

            public String getText() {
                return this.text;
            }

            @Override
            public String toString() {
                return "Headline{" +
                        "text='" + this.text + '\'' +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Head{" +
                    "title='" + this.title + '\'' +
                    ", reportDateTime='" + this.reportDateTime + '\'' +
                    ", targetDateTime=" + this.targetDateTime +
                    ", targetDTDubious='" + this.targetDTDubious + '\'' +
                    ", targetDuration='" + this.targetDuration + '\'' +
                    ", validDateTime='" + this.validDateTime + '\'' +
                    ", eventID='" + this.eventID + '\'' +
                    ", infoType=" + this.infoType +
                    ", serial='" + this.serial + '\'' +
                    ", infoKind='" + this.infoKind + '\'' +
                    ", infoKindVersion='" + this.infoKindVersion + '\'' +
                    ", headline=" + this.headline +
                    '}';
        }
    }

    @Override
    public String getDataType() {
        return "quake_info";
    }

    @Override
    public Object toExternalDto() {
        String reportDateTime = null;
        String title = null;
        String infoType = null;
        String serial = null;
        String status = null;
        long eventId = 0;
        
        if (this.control != null) {
            if (this.control.getDateTime() != null) {
                reportDateTime = this.control.getDateTime().toString();
            }
            title = this.control.getTitle();
            if (this.control.getStatus() != null) {
                status = this.control.getStatus().toString();
            }
        }
        
        if (this.head != null) {
            if (this.head.getInfoType() != null) {
                infoType = this.head.getInfoType().toString();
            }
            serial = this.head.getSerial();
            eventId = this.head.getEventID();
        }
        
        // Basic implementation - subclasses should override for specific data
        return new QuakeInfoExternalData(
                eventId,
                reportDateTime,
                title,
                infoType,
                serial,
                status,
                null, // epicenter - to be filled by subclasses
                null, // originTime - to be filled by subclasses
                null, // depth - to be filled by subclasses
                null, // magnitude - to be filled by subclasses
                null, // maxIntensity - to be filled by subclasses
                new java.util.ArrayList<>(), // intensityAreas - to be filled by subclasses
                null, // text - to be filled by subclasses
                null  // comments - to be filled by subclasses
        );
    }

    @Override
    public String toString() {
        return "AbstractJMAReport{" +
                "control=" + this.control +
                ", head=" + this.head +
                '}';
    }
}
