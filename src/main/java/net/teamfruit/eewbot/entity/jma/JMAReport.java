package net.teamfruit.eewbot.entity.jma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import net.teamfruit.eewbot.entity.Entity;

@JacksonXmlRootElement(localName = "Report")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class JMAReport implements Entity {

    private Control control;
    private Head head;

    public Control getControl() {
        return this.control;
    }

    public Head getHead() {
        return this.head;
    }

    public static class Control {

        private String title;
        private String dateTime;
        private JMAStatus status;
        private String editorialOffice;
        private String publishingOffice;

        public String getTitle() {
            return this.title;
        }

        public String getDateTime() {
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
    }

    public static class Head {

        private String title;
        private String reportDateTime;
        private String targetDateTime;
        private String targetDTDubious;
        private String targetDuration;
        private String validDateTime;
        private String eventID;
        private String infoType;
        private String serial;
        private String infoKind;
        private String infoKindVersion;
        private Headline headline;

        public String getTitle() {
            return this.title;
        }

        public String getReportDateTime() {
            return this.reportDateTime;
        }

        public String getTargetDateTime() {
            return this.targetDateTime;
        }

        public String getTargetDTDubious() {
            return this.targetDTDubious;
        }

        public String getTargetDuration() {
            return this.targetDuration;
        }

        public String getValidDateTime() {
            return this.validDateTime;
        }

        public String getEventID() {
            return this.eventID;
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

        public Headline getHeadline() {
            return this.headline;
        }

        public static class Headline {

            private String text;
            // Ignore information

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
                    ", targetDateTime='" + this.targetDateTime + '\'' +
                    ", targetDTDubious='" + this.targetDTDubious + '\'' +
                    ", targetDuration='" + this.targetDuration + '\'' +
                    ", validDateTime='" + this.validDateTime + '\'' +
                    ", eventID='" + this.eventID + '\'' +
                    ", infoType='" + this.infoType + '\'' +
                    ", serial='" + this.serial + '\'' +
                    ", infoKind='" + this.infoKind + '\'' +
                    ", infoKindVersion='" + this.infoKindVersion + '\'' +
                    ", headline=" + this.headline +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "JMAReport{" +
                "control=" + this.control +
                ", head=" + this.head +
                '}';
    }
}
