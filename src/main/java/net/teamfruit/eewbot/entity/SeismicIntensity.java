package net.teamfruit.eewbot.entity;

import java.awt.Color;
import java.util.Optional;
import java.util.stream.Stream;

public enum SeismicIntensity {
	ONE("1", "1", new Color(127, 140, 141)),
	TWO("2", "2", new Color(41, 128, 185)),
	THREE("3", "3", new Color(39, 174, 96)),
	FOUR("4", "4", new Color(221, 176, 0)),
	FIVE_MINUS("5弱", "5-", new Color(230, 126, 34)),
	FIVE_PLUS("5強", "5+", new Color(182, 72, 18)),
	SIX_MINUS("6弱", "6-", new Color(255, 118, 188)),
	SIX_PLUS("6強", "6+", new Color(255, 46, 18)),
	SEVEN("7", "7", new Color(114, 0, 172));

	private final String name;
	private final String symbol;
	private final Color color;

	private SeismicIntensity(final String name, final String symbol, final Color color) {
		this.name = name;
		this.symbol = symbol;
		this.color = color;
	}

	public String getSimple() {
		return this.name;
	}

	public String getSymbolIntensity() {
		return this.symbol;
	}

	public Color getColor() {
		return this.color;
	}

	@Override
	public String toString() {
		return this.name;
	}

	public static Optional<SeismicIntensity> get(final String name) {
		return Optional.ofNullable(Stream.of(values()).filter(value -> value.getSimple().equals(name)||value.getSymbolIntensity().equals(name)).findAny().orElse(null));
	}

}
