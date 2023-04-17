package net.teamfruit.eewbot.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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

	@JsonProperty("record")
	@JacksonXmlElementWrapper(useWrapping = false)
	private List<Record> records;

	public List<Record> getRecords() {
		return records;
	}

	public void setRecords(List<Record> records) {
		this.records = records;
	}

	public static class Record {

		@JsonProperty("date")
		@JsonFormat(pattern = "yyyy年MM月dd日")
		private LocalDate localDate;

		@JsonProperty("item")
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

		public static class Item {

			@JacksonXmlProperty(localName = "time")
			private String time;
			@JacksonXmlProperty(localName = "shindo")
			private String intensity;
			@JacksonXmlProperty(localName = "url")
			private String url;

			@JacksonXmlText
			private String value;

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
				return value;
			}

			public void setEpicenter(String value) {
				this.value = value;
			}
		}
	}
}
