package net.teamfruit.eewbot.entity.other;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDate;
import java.util.List;

@JsonRootName("jishinReport")
public class NHKQuakeInfo {

    public static final ObjectMapper QUAKE_INFO_MAPPER = XmlMapper.builder().addModule(new JavaTimeModule()).build();

    @JacksonXmlProperty(localName = "record")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Record> records;

    public List<Record> getRecords() {
        return this.records;
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }

    @Override
    public String toString() {
        return "QuakeInfo{" +
                "records=" + this.records +
                '}';
    }

    public static class Record {

        @JacksonXmlProperty(localName = "date", isAttribute = true)
        @JsonFormat(pattern = "yyyy年MM月dd日")
        private LocalDate localDate;

        @JacksonXmlProperty(localName = "item")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<Item> items;

        public LocalDate getLocalDate() {
            return this.localDate;
        }

        public void setLocalDate(LocalDate localDate) {
            this.localDate = localDate;
        }

        public List<Item> getItems() {
            return this.items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }

        @Override
        public String toString() {
            return "Record{" +
                    "localDate=" + this.localDate +
                    ", items=" + this.items +
                    '}';
        }

        public static class Item {

            @JacksonXmlProperty(localName = "time", isAttribute = true)
            private String time;

            @JacksonXmlProperty(localName = "shindo", isAttribute = true)
            private String intensity;

            @JacksonXmlProperty(localName = "url", isAttribute = true)
            private String url;

            @JacksonXmlText
            private String epicenter;

            public String getTime() {
                return this.time;
            }

            public void setTime(String time) {
                this.time = time;
            }

            public String getIntensity() {
                return this.intensity;
            }

            public void setIntensity(String intensity) {
                this.intensity = intensity;
            }

            public String getUrl() {
                return this.url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public String getEpicenter() {
                return this.epicenter;
            }

            public void setEpicenter(String value) {
                this.epicenter = value;
            }

            @Override
            public String toString() {
                return "Item{" +
                        "time='" + this.time + '\'' +
                        ", intensity='" + this.intensity + '\'' +
                        ", url='" + this.url + '\'' +
                        ", epicenter='" + this.epicenter + '\'' +
                        '}';
            }
        }
    }
}
