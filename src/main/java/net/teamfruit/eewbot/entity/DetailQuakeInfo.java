package net.teamfruit.eewbot.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "Root")
public class DetailQuakeInfo {

	private LocalDateTime timestamp;
	private Earthquake earthQuake;

	@XmlElement(name = "Timestamp")
	@XmlJavaTypeAdapter(DateAdapter.class)
	public LocalDateTime getTimestamp() {
		return this.timestamp;
	}

	public void setTimestamp(final LocalDateTime timestamp) {
		this.timestamp = timestamp;
	}

	@XmlElement(name = "Earthquake")
	public Earthquake getEarthquake() {
		return this.earthQuake;
	}

	public void setEarthquake(final Earthquake earthQuake) {
		this.earthQuake = earthQuake;
	}

	public static class Earthquake {

		private String id;
		private LocalDateTime time;
		private String intensity;
		private String epicenter;
		private String lat;
		private String lon;
		private String magnitude;
		private String depth;

		private String detail;
		private String local;
		private String global;

		private Relative relative;

		@XmlAttribute(name = "Id")
		public String getId() {
			return this.id;
		}

		public void setId(final String id) {
			this.id = id;
		}

		@XmlAttribute(name = "Time")
		@XmlJavaTypeAdapter(DateAdapter.class)
		public LocalDateTime getTime() {
			return this.time;
		}

		public void setTime(final LocalDateTime time) {
			this.time = time;
		}

		@XmlAttribute(name = "Intensity")
		public String getIntensity() {
			return this.intensity;
		}

		public void setIntensity(final String intensity) {
			this.intensity = intensity;
		}

		@XmlAttribute(name = "Epicenter")
		public String getEpicenter() {
			return this.epicenter;
		}

		public void setEpicenter(final String epicenter) {
			this.epicenter = epicenter;
		}

		@XmlAttribute(name = "Latitude")
		public String getLat() {
			return this.lat;
		}

		public void setLat(final String lat) {
			this.lat = lat;
		}

		@XmlAttribute(name = "Longitude")
		public String getLon() {
			return this.lon;
		}

		public void setLon(final String lon) {
			this.lon = lon;
		}

		@XmlAttribute(name = "Magnitude")
		public String getMagnitude() {
			return this.magnitude;
		}

		public void setMagnitude(final String magnitude) {
			this.magnitude = magnitude;
		}

		@XmlAttribute(name = "Depth")
		public String getDepth() {
			return this.depth;
		}

		public void setDepth(final String depth) {
			this.depth = depth;
		}

		@XmlElement(name = "Detail")
		public String getDetail() {
			return this.detail;
		}

		public void setDetail(final String detail) {
			this.detail = detail;
		}

		@XmlElement(name = "Local")
		public String getLocal() {
			return this.local;
		}

		public void setLocal(final String local) {
			this.local = local;
		}

		@XmlElement(name = "Global")
		public String getGlobal() {
			return this.global;
		}

		public void setGlobal(final String global) {
			this.global = global;
		}

		@XmlElement(name = "Relative")
		public Relative getRelative() {
			return this.relative;
		}

		public void setRelative(final Relative relative) {
			this.relative = relative;
		}

		public static class Relative {

			private List<Group> groups;

			@XmlElement(name = "Group")
			public List<Group> getGroups() {
				return this.groups;
			}

			public void setGroups(final List<Group> groups) {
				this.groups = groups;
			}

			public static class Group {

				private String intensity;
				private List<Area> areas;

				@XmlAttribute(name = "Intensity")
				public String getIntensity() {
					return this.intensity;
				}

				public void setIntensity(final String intensity) {
					this.intensity = intensity;
				}

				@XmlElement(name = "Area")
				public List<Area> getAreas() {
					return this.areas;
				}

				public void setAreas(final List<Area> areas) {
					this.areas = areas;
				}

				public static class Area {

					private String name;

					@XmlAttribute(name = "Name")
					public String getName() {
						return this.name;
					}

					public void setName(final String name) {
						this.name = name;
					}

				}
			}
		}
	}

	public static class DateAdapter extends XmlAdapter<String, LocalDateTime> {

		private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

		@Override
		public LocalDateTime unmarshal(final String v) throws Exception {
			return LocalDateTime.parse(v, this.formatter);
		}

		@Override
		public String marshal(final LocalDateTime v) throws Exception {
			return v.format(this.formatter);
		}

	}
}
