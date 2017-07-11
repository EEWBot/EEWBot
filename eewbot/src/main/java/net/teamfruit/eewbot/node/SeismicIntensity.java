package net.teamfruit.eewbot.node;

import java.awt.Color;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public enum SeismicIntensity {
	ONE("震度1", new Color(127, 140, 141)),
	TWO("震度2", new Color(41, 128, 185)),
	THREE("震度3", new Color(39, 174, 96)),
	FOUR("震度4", new Color(221, 176, 0)),
	FIVE_MINUS("震度5弱", new Color(230, 126, 34)),
	FIVE_PLUS("震度5強", new Color(182, 72, 18)),
	SIX_MINUS("震度6弱", new Color(255, 118, 188)),
	SIX_PLUS("震度6強", new Color(255, 46, 18)),
	SEVEN("震度7", new Color(114, 0, 172));

	private final String name;
	private final Color color;

	private SeismicIntensity(final String name, final Color color) {
		this.name = name;
		this.color = color;
	}

	public Color getColor() {
		return this.color;
	}

	public String getSimple() {
		return StringUtils.substring(this.name, 2, name().length());
	}

	@Override
	public String toString() {
		return this.name;
	}

	public static SeismicIntensity get(final String name) {
		return Stream.of(values()).filter(value -> value.toString().equals(name)||value.getSimple().equals(name)).findAny().orElse(null);
	}

}
