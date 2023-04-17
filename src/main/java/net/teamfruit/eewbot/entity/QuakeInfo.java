package net.teamfruit.eewbot.entity;

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
public class QuakeInfo {

    public static final ObjectMapper QUAKE_INFO_MAPPER = XmlMapper.builder().addModule(new JavaTimeModule()).build();

    @JacksonXmlProperty(localName = "record")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Record> records;

    public List<Record> getRecords() {
        return records;
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }

    @Override
    public String toString() {
        return "QuakeInfo{" +
                "records=" + records +
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
            return localDate;
        }

        public void setLocalDate(LocalDate localDate) {
            this.localDate = localDate;
        }

        public List<Item> getItems() {
            return items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }

        @Override
        public String toString() {
            return "Record{" +
                    "localDate=" + localDate +
                    ", items=" + items +
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
                return time;
            }

            public void setTime(String time) {
                this.time = time;
            }

            public String getIntensity() {
                return intensity;
            }

            public void setIntensity(String intensity) {
                this.intensity = intensity;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public String getEpicenter() {
                return epicenter;
            }

            public void setEpicenter(String value) {
                this.epicenter = value;
            }

            @Override
            public String toString() {
                return "Item{" +
                        "time='" + time + '\'' +
                        ", intensity='" + intensity + '\'' +
                        ", url='" + url + '\'' +
                        ", epicenter='" + epicenter + '\'' +
                        '}';
            }
        }
    }
}
