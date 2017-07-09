package net.teamfruit.eewbot.node;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.teamfruit.eewbot.node.QuakeInfo.PrefectureDetail.SeismicIntensity;

public class QuakeInfo {
	public static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy年M月d日 H時mm分");

	private final String imageUrl;
	private final Date announceTime;
	private final Date quakeTime;
	private final String epicenter;
	private final String lat;
	private final String lon;
	private final String depth;
	private final float magnitude;
	private final String info;
	private final SeismicIntensity maxIntensity;
	private final Set<PrefectureDetail> details = new TreeSet<>(Comparator.reverseOrder());

	public QuakeInfo(final Document doc) {
		this.imageUrl = doc.getElementById("yjw_keihou").getElementsByTag("img").first().attr("src");
		final Element info = doc.getElementById("eqinfdtl");
		final Map<String, String> data = info.getElementsByTag("table").get(0).getElementsByTag("tr").stream()
				.map(tr -> tr.getElementsByTag("td")).collect(Collectors.toMap(td -> td.get(0).text(), td -> td.get(1).text()));

		try {
			this.announceTime = FORMAT.parse(data.get("情報発表時刻"));
			final String quakeTime = data.get("発生時刻");
			this.quakeTime = FORMAT.parse(StringUtils.substring(quakeTime, 0, quakeTime.length()-2));
			this.epicenter = data.get("震源地");
			this.lat = data.get("緯度");
			this.lon = data.get("経度");
			this.depth = data.get("深さ");
			this.magnitude = Float.parseFloat(data.get("マグニチュード"));
			this.info = data.get("情報");
		} catch (final ParseException e) {
			throw new RuntimeException("Parse Error", e);
		}

		final Elements yjw = info.getElementsByTag("table").get(1).getElementsByTag("tr");
		this.maxIntensity = SeismicIntensity.get(yjw.first().getElementsByTag("td").first().text());
		yjw.forEach(tr -> {
			final SeismicIntensity intensity = SeismicIntensity.get(tr.getElementsByTag("td").first().text());
			final Elements td = tr.getElementsByTag("table").first().getElementsByTag("td");
			final String prefecture = td.get(0).text();
			final PrefectureDetail detail = this.details.stream().filter(line -> line.getPrefecture().equals(prefecture)).findAny()
					.orElseGet(() -> new PrefectureDetail(prefecture));
			this.details.add(detail);
			detail.addCity(intensity, prefecture);
		});
	}

	public static SimpleDateFormat getFormat() {
		return FORMAT;
	}

	public String getImageUrl() {
		return this.imageUrl;
	}

	public Date getAnnounceTime() {
		return this.announceTime;
	}

	public Date getQuakeTime() {
		return this.quakeTime;
	}

	public String getEpicenter() {
		return this.epicenter;
	}

	public String getLat() {
		return this.lat;
	}

	public String getLon() {
		return this.lon;
	}

	public String getDepth() {
		return this.depth;
	}

	public float getMagnitude() {
		return this.magnitude;
	}

	public String getInfo() {
		return this.info;
	}

	public SeismicIntensity getMaxIntensity() {
		return this.maxIntensity;
	}

	public Set<PrefectureDetail> getDetails() {
		return this.details;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime*result+((this.announceTime==null) ? 0 : this.announceTime.hashCode());
		result = prime*result+((this.depth==null) ? 0 : this.depth.hashCode());
		result = prime*result+((this.details==null) ? 0 : this.details.hashCode());
		result = prime*result+((this.epicenter==null) ? 0 : this.epicenter.hashCode());
		result = prime*result+((this.imageUrl==null) ? 0 : this.imageUrl.hashCode());
		result = prime*result+((this.info==null) ? 0 : this.info.hashCode());
		result = prime*result+((this.lat==null) ? 0 : this.lat.hashCode());
		result = prime*result+((this.lon==null) ? 0 : this.lon.hashCode());
		result = prime*result+Float.floatToIntBits(this.magnitude);
		result = prime*result+((this.quakeTime==null) ? 0 : this.quakeTime.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this==obj)
			return true;
		if (obj==null)
			return false;
		if (!(obj instanceof QuakeInfo))
			return false;
		final QuakeInfo other = (QuakeInfo) obj;
		if (this.announceTime==null) {
			if (other.announceTime!=null)
				return false;
		} else if (!this.announceTime.equals(other.announceTime))
			return false;
		if (this.depth==null) {
			if (other.depth!=null)
				return false;
		} else if (!this.depth.equals(other.depth))
			return false;
		if (this.details==null) {
			if (other.details!=null)
				return false;
		} else if (!this.details.equals(other.details))
			return false;
		if (this.epicenter==null) {
			if (other.epicenter!=null)
				return false;
		} else if (!this.epicenter.equals(other.epicenter))
			return false;
		if (this.imageUrl==null) {
			if (other.imageUrl!=null)
				return false;
		} else if (!this.imageUrl.equals(other.imageUrl))
			return false;
		if (this.info==null) {
			if (other.info!=null)
				return false;
		} else if (!this.info.equals(other.info))
			return false;
		if (this.lat==null) {
			if (other.lat!=null)
				return false;
		} else if (!this.lat.equals(other.lat))
			return false;
		if (this.lon==null) {
			if (other.lon!=null)
				return false;
		} else if (!this.lon.equals(other.lon))
			return false;
		if (Float.floatToIntBits(this.magnitude)!=Float.floatToIntBits(other.magnitude))
			return false;
		if (this.quakeTime==null) {
			if (other.quakeTime!=null)
				return false;
		} else if (!this.quakeTime.equals(other.quakeTime))
			return false;
		return true;
	}

	public static class PrefectureDetail implements Comparable<PrefectureDetail> {

		private final String prefecture;
		private final Map<SeismicIntensity, List<String>> cities = new TreeMap<>(Comparator.reverseOrder());

		public PrefectureDetail(final String prefecture) {
			this.prefecture = prefecture;
		}

		public String getPrefecture() {
			return this.prefecture;
		}

		public Map<SeismicIntensity, List<String>> getCities() {
			return this.cities;
		}

		public void addCity(final SeismicIntensity intensity, final String city) {
			this.cities.computeIfAbsent(intensity, key -> new ArrayList<>()).add(city);
		}

		public Optional<SeismicIntensity> getMaxIntensity() {
			if (this.cities.size()>0)
				return Optional.of(this.cities.keySet().iterator().next());
			return Optional.empty();
		}

		@Override
		public int compareTo(final PrefectureDetail o) {
			return getMaxIntensity().map(intensity -> intensity.compareTo(o.getMaxIntensity().orElseThrow(() -> new IllegalArgumentException())))
					.orElseThrow(() -> new IllegalStateException());
		};

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime*result+((this.prefecture==null) ? 0 : this.prefecture.hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this==obj)
				return true;
			if (obj==null)
				return false;
			if (!(obj instanceof PrefectureDetail))
				return false;
			final PrefectureDetail other = (PrefectureDetail) obj;
			if (this.prefecture==null) {
				if (other.prefecture!=null)
					return false;
			} else if (!this.prefecture.equals(other.prefecture))
				return false;
			return true;
		}

		public static enum SeismicIntensity {
			ONE("震度1"),
			TWO("震度2"),
			THREE("震度3"),
			FOUR("震度4"),
			FIVE_MINUS("震度5弱"),
			FIVE_PLUS("震度5強"),
			SIX_MINUS("震度6弱"),
			SIX_PLUS("震度6強"),
			SEVEN("震度7");

			private final String name;

			private SeismicIntensity(final String name) {
				this.name = name;
			}

			@Override
			public String toString() {
				return this.name;
			}

			public static SeismicIntensity get(final String name) {
				return Stream.of(values()).filter(value -> value.toString().equals(name)).findAny().orElse(null);
			}
		}
	}
}
