package net.teamfruit.eewbot.node;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jsoup.nodes.Document;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.util.EmbedBuilder;

public class QuakeInfo implements Embeddable {
	public static final FastDateFormat FORMAT = FastDateFormat.getInstance("yyyy年M月d日 H時mm分");
	public static final Pattern PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

	private final Document original;
	private final String url;
	private final Optional<String> imageUrl;
	private final Date quakeTime;
	private final String epicenter;
	private final String lat;
	private final String lon;
	private final String depth;
	private final float magnitude;
	private final String info;
	private final Optional<SeismicIntensity> maxIntensity;
	private final List<PrefectureDetail> details;

	public QuakeInfo(final Document doc) {
		this.original = doc;
		this.url = "https://typhoon.yahoo.co.jp"+Optional.ofNullable(doc.getElementById("history")).map(history -> history.getElementsByTag("tr").get(1).getElementsByTag("td").first().getElementsByTag("a").first().attr("href")).orElse("");
		this.imageUrl = Optional.ofNullable(doc.getElementById("yjw_keihou").getElementsByTag("img").first()).map(image -> StringUtils.substringBefore(image.attr("src"), "?"));

		try {
			final String quakeTime = doc.select("#eqinfdtl > table.yjw_table.boderset > tbody > tr:nth-child(1) > td:nth-child(2) > small").text();
			this.quakeTime = FORMAT.parse(StringUtils.substring(quakeTime, 0, quakeTime.length()-2));
			this.epicenter = doc.select("#eqinfdtl > table.yjw_table.boderset > tbody > tr:nth-child(2) > td:nth-child(2) > small > a").text();
			final Matcher matcher = PATTERN.matcher(doc.select("#eqinfdtl > table.yjw_table.boderset > tbody > tr:nth-child(6) > td:nth-child(2) > small").text());
			matcher.find();
			this.lat = matcher.group();
			matcher.find();
			this.lon = matcher.group();
			this.depth = doc.select("#eqinfdtl > table.yjw_table.boderset > tbody > tr:nth-child(5) > td:nth-child(2) > small").text();
			this.magnitude = NumberUtils.toFloat(doc.select("#eqinfdtl > table.yjw_table.boderset > tbody > tr:nth-child(4) > td:nth-child(2) > small").text(), -1f);
			this.info = StringUtils.trim(StringUtils.remove(doc.select("#eqinfdtl > table.yjw_table.boderset > tbody > tr:nth-child(7) > td:nth-child(2) > small").text(), ">>津波情報を見る"));
		} catch (final ParseException e) {
			throw new RuntimeException("Parse Error", e);
		}

		this.maxIntensity = SeismicIntensity.get(doc.select("#eqinfdtl > table.yjw_table.boderset > tbody > tr:nth-child(3) > td:nth-child(2) > small").text());

		final Map<String, PrefectureDetail> details = new HashMap<>();
		doc.getElementById("eqinfdtl").getElementsByTag("table").get(1).getElementsByTag("tr").stream().filter(tr -> tr.attr("valign").equals("middle")).forEach(tr -> {
			final Optional<SeismicIntensity> intensity = SeismicIntensity.get(tr.getElementsByTag("td").first().getElementsByTag("td").first().text());
			tr.getElementsByTag("table").first().getElementsByTag("tr").stream().map(line -> line.getElementsByTag("td")).collect(Collectors.toMap(td -> td.get(0).text(), td -> td.get(1).text())).entrySet().forEach(entry -> {
				final String prefecture = entry.getKey();
				final PrefectureDetail detail = details.computeIfAbsent(prefecture, key -> new PrefectureDetail(prefecture));
				intensity.ifPresent(line -> Stream.of(StringUtils.split(entry.getValue(), "　")).forEach(str -> detail.addCity(line, str)));
			});
		});

		this.details = new ArrayList<>(details.values());
		Collections.sort(this.details, Comparator.reverseOrder());
	}

	public Document getOriginal() {
		return this.original;
	}

	public String getUrl() {
		return this.url;
	}

	public Optional<String> getImageUrl() {
		return this.imageUrl;
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

	public Optional<SeismicIntensity> getMaxIntensity() {
		return this.maxIntensity;
	}

	public boolean isPreliminaryReport() {
		return getEpicenter().equals("---")&&getDepth().equals("---");
	}

	public List<PrefectureDetail> getDetails() {
		if (isPreliminaryReport())
			return Collections.emptyList();
		return this.details;
	}

	public List<EmbedObject> getDetailsEmbed() {
		if (isPreliminaryReport())
			return Collections.emptyList();
		return getDetails().stream().map(PrefectureDetail::buildEmbed).collect(Collectors.toList());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime*result+(this.depth==null ? 0 : this.depth.hashCode());
		result = prime*result+(this.details==null ? 0 : this.details.hashCode());
		result = prime*result+(this.epicenter==null ? 0 : this.epicenter.hashCode());
		result = prime*result+(this.info==null ? 0 : this.info.hashCode());
		result = prime*result+(this.lat==null ? 0 : this.lat.hashCode());
		result = prime*result+(this.lon==null ? 0 : this.lon.hashCode());
		result = prime*result+Float.floatToIntBits(this.magnitude);
		result = prime*result+(this.maxIntensity==null ? 0 : this.maxIntensity.hashCode());
		result = prime*result+(this.quakeTime==null ? 0 : this.quakeTime.hashCode());
		result = prime*result+(this.url==null ? 0 : this.url.hashCode());
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
		if (this.maxIntensity==null) {
			if (other.maxIntensity!=null)
				return false;
		} else if (!this.maxIntensity.equals(other.maxIntensity))
			return false;
		if (this.quakeTime==null) {
			if (other.quakeTime!=null)
				return false;
		} else if (!this.quakeTime.equals(other.quakeTime))
			return false;
		if (this.url==null) {
			if (other.url!=null)
				return false;
		} else if (!this.url.equals(other.url))
			return false;
		return true;
	}

	public boolean isImageUpdate(final QuakeInfo other) {
		if (this==other)
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
		if (this.maxIntensity==null) {
			if (other.maxIntensity!=null)
				return false;
		} else if (!this.maxIntensity.equals(other.maxIntensity))
			return false;
		if (this.quakeTime==null) {
			if (other.quakeTime!=null)
				return false;
		} else if (!this.quakeTime.equals(other.quakeTime))
			return false;
		if (this.url==null) {
			if (other.url!=null)
				return false;
		} else if (!this.url.equals(other.url))
			return false;
		if (this.imageUrl==null) {
			if (other.imageUrl!=null)
				return true;
		} else if (this.imageUrl.equals(other.imageUrl))
			return false;
		return true;
	}

	public boolean useDataEquals(final QuakeInfo info) {
		if (!getEpicenter().equals(info.getEpicenter()))
			return false;
		if (!getDepth().equals(info.getDepth()))
			return false;
		if (getMagnitude()!=info.getMagnitude())
			return false;
		if (getMaxIntensity().isPresent()&&info.getMaxIntensity().isPresent()) {
			if (getMaxIntensity().get()!=info.getMaxIntensity().get())
				return false;
		} else if (getMaxIntensity().isPresent()!=info.getMaxIntensity().isPresent())
			return false;
		if (!getInfo().equals(info.getInfo()))
			return false;
		if (!getImageUrl().equals(info.getImageUrl()))
			return false;
		//		if (!getUrl().equals(info.getUrl()))
		//			return false;
		return true;
	}

	@Override
	public EmbedObject buildEmbed() {
		final EmbedBuilder builder = new EmbedBuilder();
		if (isPreliminaryReport()) {
			builder.withTitle("地震速報");
			final Map<SeismicIntensity, List<String>> map = new TreeMap<>(Comparator.reverseOrder());
			getDetails().forEach(detail -> detail.getCities().entrySet().forEach(city -> {
				final List<String> list = map.get(city.getKey());
				if (list==null)
					map.put(city.getKey(), new ArrayList<>(city.getValue()));
				else
					list.addAll(city.getValue());
			}));
			map.entrySet().forEach(entry -> builder.appendField(entry.getKey().toString(), String.join("  ", entry.getValue()), false));
		} else {
			builder.withTitle("地震情報");
			builder.appendField("震央", getEpicenter(), true);
			if (!getDepth().equals("---"))
				builder.appendField("深さ", getDepth(), true);
			if (getMagnitude()>0f)
				builder.appendField("マグニチュード", String.valueOf(getMagnitude()), true);
			getMaxIntensity().ifPresent(intensity -> builder.appendField("最大震度", intensity.getSimple(), false));
		}
		builder.appendField("情報", getInfo(), true);

		getMaxIntensity().ifPresent(intensity -> builder.withColor(intensity.getColor()));
		builder.withTimestamp(getQuakeTime().getTime());
		getImageUrl().ifPresent(url -> builder.withImage(url));

		builder.withFooterText("地震情報 - Yahoo!天気・災害");
		builder.withUrl(getUrl());
		return builder.build();
	}

	@Override
	public String toString() {
		return "QuakeInfo [url="+this.url+", imageUrl="+this.imageUrl+", quakeTime="+this.quakeTime+", epicenter="+this.epicenter+", lat="+this.lat+", lon="+this.lon+", depth="+this.depth+", magnitude="+this.magnitude+", info="+this.info+", maxIntensity="+this.maxIntensity+", details="+this.details+"]";
	}

	public static class PrefectureDetail implements Comparable<PrefectureDetail>, Embeddable {

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
		}

		@Override
		public EmbedObject buildEmbed() {
			final EmbedBuilder builder = new EmbedBuilder();

			getCities().entrySet().stream().forEach(entry -> builder.appendField(entry.getKey().toString(), String.join("  ", entry.getValue()), false));

			getMaxIntensity().ifPresent(intensity -> builder.withColor(intensity.getColor()));
			builder.withTitle(getPrefecture());
			return builder.build();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime*result+(this.cities==null ? 0 : this.cities.hashCode());
			result = prime*result+(this.prefecture==null ? 0 : this.prefecture.hashCode());
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
			if (this.cities==null) {
				if (other.cities!=null)
					return false;
			} else if (!this.cities.equals(other.cities))
				return false;
			if (this.prefecture==null) {
				if (other.prefecture!=null)
					return false;
			} else if (!this.prefecture.equals(other.prefecture))
				return false;
			return true;
		}

	}

}
