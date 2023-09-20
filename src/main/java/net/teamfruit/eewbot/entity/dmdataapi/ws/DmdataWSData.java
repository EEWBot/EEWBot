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

    public String getClassification() {
        return classification;
    }

    public String getId() {
        return id;
    }

    public List<Passing> getPassing() {
        return passing;
    }

    public Head getHead() {
        return head;
    }

    public XMLReport getXmlReport() {
        return xmlReport;
    }

    public String getFormat() {
        return format;
    }

    public String getCompression() {
        return compression;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getBody() {
        return body;
    }

    public static class Passing {

        private String name;
        private String time;

        public String getName() {
            return name;
        }

        public String getTime() {
            return time;
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

        public String getAuthor() {
            return author;
        }

        public String getTime() {
            return time;
        }

        public boolean isTest() {
            return test;
        }

        public boolean isXml() {
            return xml;
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

        public HeadXML getHead() {
            return head;
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

            public String getDateTime() {
                return dateTime;
            }

            public String getStatus() {
                return status;
            }

            public String getEditorialOffice() {
                return editorialOffice;
            }

            public String getPublishingOffice() {
                return publishingOffice;
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

            public String getReportDateTime() {
                return reportDateTime;
            }

            public String getTargetDateTime() {
                return targetDateTime;
            }

            public String getEventId() {
                return eventId;
            }

            public String getSerial() {
                return serial;
            }

            public String getInfoType() {
                return infoType;
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
