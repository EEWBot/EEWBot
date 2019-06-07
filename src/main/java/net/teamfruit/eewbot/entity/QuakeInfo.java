package net.teamfruit.eewbot.entity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "jishinReport")
public class QuakeInfo {

	private List<Record> records;

	@XmlElement(name = "record")
	public List<Record> getRecords() {
		return this.records;
	}

	public void setRecords(final List<Record> records) {
		this.records = records;
	}

	public static class Record {

		private List<Item> items;
		private LocalDate localDate;

		@XmlElement(name = "item")
		public List<Item> getItems() {
			return this.items;
		}

		public void setItems(final List<Item> items) {
			this.items = items;
		}

		@XmlAttribute(name = "date")
		@XmlJavaTypeAdapter(DateAdapter.class)
		public LocalDate getLocalDate() {
			return this.localDate;
		}

		public void setLocalDate(final LocalDate localDate) {
			this.localDate = localDate;
		}

		public static class Item {

			private String time;
			private String intensity;
			private String url;
			private String epicenter;

			@XmlAttribute
			public String getTime() {
				return this.time;
			}

			public void setTime(final String time) {
				this.time = time;
			}

			@XmlAttribute(name = "shindo")
			public String getIntensity() {
				return this.intensity;
			}

			public void setIntensity(final String intensity) {
				this.intensity = intensity;
			}

			@XmlAttribute
			public String getUrl() {
				return this.url;
			}

			public void setUrl(final String url) {
				this.url = url;
			}

			@XmlValue
			public String getEpicenter() {
				return this.epicenter;
			}

			public void setEpicenter(final String epicenter) {
				this.epicenter = epicenter;
			}

		}

		public static class DateAdapter extends XmlAdapter<String, LocalDate> {

			private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

			@Override
			public LocalDate unmarshal(final String v) throws Exception {
				return LocalDate.parse(v, this.formatter);
			}

			@Override
			public String marshal(final LocalDate v) throws Exception {
				return v.format(this.formatter);
			}

		}
	}
}
