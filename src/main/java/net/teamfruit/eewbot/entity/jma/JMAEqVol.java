package net.teamfruit.eewbot.entity.jma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.List;

@JacksonXmlRootElement(localName = "feed")
@JsonIgnoreProperties(ignoreUnknown = true)
public class JMAEqVol {

    @JacksonXmlText
    private String updated;

    @JacksonXmlProperty(localName = "entry")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Entry> entries;

    public String getUpdated() {
        return this.updated;
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    public static class Entry {

        private String title;
        private String id;
        private String updated;
        private Author author;
        private Link link;
        private Content content;

        public String getTitle() {
            return this.title;
        }

        public String getId() {
            return this.id;
        }

        public String getUpdated() {
            return this.updated;
        }

        public Author getAuthor() {
            return this.author;
        }

        public Link getLink() {
            return this.link;
        }

        public Content getContent() {
            return this.content;
        }

        public static class Author {

            private String name;

            public String getName() {
                return this.name;
            }

            @Override
            public String toString() {
                return "Author{" +
                        "name='" + this.name + '\'' +
                        '}';
            }
        }

        public static class Link {

            @JacksonXmlProperty(localName = "type", isAttribute = true)
            private String type;

            @JacksonXmlProperty(localName = "href", isAttribute = true)
            private String href;

            public String getType() {
                return this.type;
            }

            public String getHref() {
                return this.href;
            }

            @Override
            public String toString() {
                return "Link{" +
                        "type='" + this.type + '\'' +
                        ", href='" + this.href + '\'' +
                        '}';
            }
        }

        public static class Content {

            @JacksonXmlProperty(localName = "type", isAttribute = true)
            private String type;

            @JacksonXmlText
            private String text;

            public String getType() {
                return this.type;
            }

            public String getText() {
                return this.text;
            }

            @Override
            public String toString() {
                return "Content{" +
                        "type='" + this.type + '\'' +
                        ", text='" + this.text + '\'' +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "Entry{" +
                    "title='" + this.title + '\'' +
                    ", id='" + this.id + '\'' +
                    ", updated='" + this.updated + '\'' +
                    ", author=" + this.author +
                    ", link=" + this.link +
                    ", content=" + this.content +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "JMAEqVol{" +
                "updated='" + this.updated + '\'' +
                ", entries=" + this.entries +
                '}';
    }
}
