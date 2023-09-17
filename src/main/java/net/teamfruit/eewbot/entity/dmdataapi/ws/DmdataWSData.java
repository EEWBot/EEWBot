package net.teamfruit.eewbot.entity.dmdataapi.ws;

import java.util.List;

public class DmdataWSData extends DmdataWSMessage {

    private String version;
    private String classification;
    private String id;
    private List<Passing> passing;
    private Head head;
    private XMLReport xmlReport;
    private String format;
    private String compression;
    private String encoding;
    private String body;

    public DmdataWSData(Type type) {
        super(Type.DATA);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Passing> getPassing() {
        return passing;
    }

    public void setPassing(List<Passing> passing) {
        this.passing = passing;
    }

    public Head getHead() {
        return head;
    }

    public void setHead(Head head) {
        this.head = head;
    }

    public XMLReport getXmlReport() {
        return xmlReport;
    }

    public void setXmlReport(XMLReport xmlReport) {
        this.xmlReport = xmlReport;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public static class Passing {

        private String name;
        private String time;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        @Override
        public String toString() {
            return "Passing{" +
                    "name='" + name + '\'' +
                    ", time='" + time + '\'' +
                    '}';
        }
    }

    public static class Head {

        private String type;
        private String author;
        private String time;
        private boolean test;
        private boolean xml;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public boolean isTest() {
            return test;
        }

        public void setTest(boolean test) {
            this.test = test;
        }

        public boolean isXml() {
            return xml;
        }

        public void setXml(boolean xml) {
            this.xml = xml;
        }

        @Override
        public String toString() {
            return "Head{" +
                    "type='" + type + '\'' +
                    ", author='" + author + '\'' +
                    ", time='" + time + '\'' +
                    ", test=" + test +
                    ", xml=" + xml +
                    '}';
        }
    }

    public static class XMLReport {

        private Control control;
        private HeadXML head;

        public Control getControl() {
            return control;
        }

        public void setControl(Control control) {
            this.control = control;
        }

        public HeadXML getHead() {
            return head;
        }

        public void setHead(HeadXML head) {
            this.head = head;
        }

        public static class Control {

            private String title;
            private String dateTime;
            private String status;
            private String editorialOffice;
            private String publishingOffice;

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public String getDateTime() {
                return dateTime;
            }

            public void setDateTime(String dateTime) {
                this.dateTime = dateTime;
            }

            public String getStatus() {
                return status;
            }

            public void setStatus(String status) {
                this.status = status;
            }

            public String getEditorialOffice() {
                return editorialOffice;
            }

            public void setEditorialOffice(String editorialOffice) {
                this.editorialOffice = editorialOffice;
            }

            public String getPublishingOffice() {
                return publishingOffice;
            }

            public void setPublishingOffice(String publishingOffice) {
                this.publishingOffice = publishingOffice;
            }

            @Override
            public String toString() {
                return "Control{" +
                        "title='" + title + '\'' +
                        ", dateTime='" + dateTime + '\'' +
                        ", status='" + status + '\'' +
                        ", editorialOffice='" + editorialOffice + '\'' +
                        ", publishingOffice='" + publishingOffice + '\'' +
                        '}';
            }
        }

        public static class HeadXML {

            private String title;
            private String reportDateTime;
            private String targetDateTime;
            private String eventId;
            private String serial;
            private String infoType;
            private String infoKind;
            private String infoKindVersion;
            private String headline;

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public String getReportDateTime() {
                return reportDateTime;
            }

            public void setReportDateTime(String reportDateTime) {
                this.reportDateTime = reportDateTime;
            }

            public String getTargetDateTime() {
                return targetDateTime;
            }

            public void setTargetDateTime(String targetDateTime) {
                this.targetDateTime = targetDateTime;
            }

            public String getEventId() {
                return eventId;
            }

            public void setEventId(String eventId) {
                this.eventId = eventId;
            }

            public String getSerial() {
                return serial;
            }

            public void setSerial(String serial) {
                this.serial = serial;
            }

            public String getInfoType() {
                return infoType;
            }

            public void setInfoType(String infoType) {
                this.infoType = infoType;
            }

            public String getInfoKind() {
                return infoKind;
            }

            public void setInfoKind(String infoKind) {
                this.infoKind = infoKind;
            }

            public String getInfoKindVersion() {
                return infoKindVersion;
            }

            public void setInfoKindVersion(String infoKindVersion) {
                this.infoKindVersion = infoKindVersion;
            }

            public String getHeadline() {
                return headline;
            }

            public void setHeadline(String headline) {
                this.headline = headline;
            }

            @Override
            public String toString() {
                return "HeadXML{" +
                        "title='" + title + '\'' +
                        ", reportDateTime='" + reportDateTime + '\'' +
                        ", targetDateTime='" + targetDateTime + '\'' +
                        ", eventId='" + eventId + '\'' +
                        ", serial='" + serial + '\'' +
                        ", infoType='" + infoType + '\'' +
                        ", infoKind='" + infoKind + '\'' +
                        ", infoKindVersion='" + infoKindVersion + '\'' +
                        ", headline='" + headline + '\'' +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "XMLReport{" +
                    "control=" + control +
                    ", head=" + head +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataWSData{" +
                "version='" + version + '\'' +
                ", classification='" + classification + '\'' +
                ", id='" + id + '\'' +
                ", passing=" + passing +
                ", head=" + head +
                ", xmlReport=" + xmlReport +
                ", format='" + format + '\'' +
                ", compression='" + compression + '\'' +
                ", encoding='" + encoding + '\'' +
                ", body='" + body + '\'' +
                '}';
    }
}
