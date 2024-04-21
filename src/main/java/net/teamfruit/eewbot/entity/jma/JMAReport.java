package net.teamfruit.eewbot.entity.jma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import net.teamfruit.eewbot.entity.Entity;
import reactor.util.annotation.Nullable;

import java.time.Instant;
import java.util.Optional;

@SuppressWarnings("unused")
@JacksonXmlRootElement(localName = "Report")
//@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class JMAReport implements Entity {

    @JacksonXmlProperty(localName = "Control")
    protected Control control;

    @JacksonXmlProperty(localName = "Head")
    protected Head head;

    public Control getControl() {
        return this.control;
    }

    public Head getHead() {
        return this.head;
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
        private String eventID;

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

        public Optional<Instant> getTargetDateTime() {
            return Optional.ofNullable(this.targetDateTime);
        }

        public Optional<String> getTargetDTDubious() {
            return Optional.ofNullable(this.targetDTDubious);
        }

        public Optional<String> getTargetDuration() {
            return Optional.ofNullable(this.targetDuration);
        }

        public Optional<String> getValidDateTime() {
            return Optional.ofNullable(this.validDateTime);
        }

        public String getEventID() {
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
}
