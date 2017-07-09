package net.teamfruit.eewbot.node;

import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public enum SeismicIntensity {
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

	public String getSimple() {
		return StringUtils.substring(this.name, 2, name().length());
	}

	@Override
	public String toString() {
		return this.name;
	}

	public static SeismicIntensity get(final String name) {
		return Stream.of(values()).filter(value -> value.toString().equals(name)).findAny().orElse(null);
	}

}
