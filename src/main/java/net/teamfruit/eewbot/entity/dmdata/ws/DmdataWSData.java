package net.teamfruit.eewbot.entity.dmdata.ws;

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
        return this.version;
    }

    public String getClassification() {
        return this.classification;
    }

    public String getId() {
        return this.id;
    }

    public List<Passing> getPassing() {
        return this.passing;
    }

    public Head getHead() {
        return this.head;
    }

    public XMLReport getXmlReport() {
        return this.xmlReport;
    }

    public String getFormat() {
        return this.format;
    }

    public String getCompression() {
        return this.compression;
    }

    public String getEncoding() {
        return this.encoding;
    }

    public String getBody() {
        return this.body;
    }

    public static class Passing {

        private String name;
        private String time;

        public String getName() {
            return this.name;
        }

        public String getTime() {
            return this.time;
        }

        @Override
        public String toString() {
            return "Passing{" +
                    "name='" + this.name + '\'' +
                    ", time='" + this.time + '\'' +
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
            return this.type;
        }

        public String getAuthor() {
            return this.author;
        }

        public String getTime() {
            return this.time;
        }

        public boolean isTest() {
            return this.test;
        }

        public boolean isXml() {
            return this.xml;
        }

        @Override
        public String toString() {
            return "Head{" +
                    "type='" + this.type + '\'' +
                    ", author='" + this.author + '\'' +
                    ", time='" + this.time + '\'' +
                    ", test=" + this.test +
                    ", xml=" + this.xml +
                    '}';
        }
    }

    public static class XMLReport {

        private Control control;
        private HeadXML head;

        public Control getControl() {
            return this.control;
        }

        public HeadXML getHead() {
            return this.head;
        }

        public static class Control {

            private String title;
            private String dateTime;
            private String status;
            private String editorialOffice;
            private String publishingOffice;

            public String getTitle() {
                return this.title;
            }

            public String getDateTime() {
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

            @Override
            public String toString() {
                return "Control{" +
                        "title='" + this.title + '\'' +
                        ", dateTime='" + this.dateTime + '\'' +
                        ", status='" + this.status + '\'' +
                        ", editorialOffice='" + this.editorialOffice + '\'' +
                        ", publishingOffice='" + this.publishingOffice + '\'' +
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
                return this.title;
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

            public String getSerial() {
                return this.serial;
            }

            public String getInfoType() {
                return this.infoType;
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

            @Override
            public String toString() {
                return "HeadXML{" +
                        "title='" + this.title + '\'' +
                        ", reportDateTime='" + this.reportDateTime + '\'' +
                        ", targetDateTime='" + this.targetDateTime + '\'' +
                        ", eventId='" + this.eventId + '\'' +
                        ", serial='" + this.serial + '\'' +
                        ", infoType='" + this.infoType + '\'' +
                        ", infoKind='" + this.infoKind + '\'' +
                        ", infoKindVersion='" + this.infoKindVersion + '\'' +
                        ", headline='" + this.headline + '\'' +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "XMLReport{" +
                    "control=" + this.control +
                    ", head=" + this.head +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "DmdataWSData{" +
                "version='" + this.version + '\'' +
                ", classification='" + this.classification + '\'' +
                ", id='" + this.id + '\'' +
                ", passing=" + this.passing +
                ", head=" + this.head +
                ", xmlReport=" + this.xmlReport +
                ", format='" + this.format + '\'' +
                ", compression='" + this.compression + '\'' +
                ", encoding='" + this.encoding + '\'' +
                ", body='" + this.body + '\'' +
                '}';
    }
}
