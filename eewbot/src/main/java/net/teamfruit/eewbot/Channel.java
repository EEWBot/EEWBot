package net.teamfruit.eewbot;

public class Channel {
	private final long id;
	public boolean eewAlart;
	public boolean eewPrediction;

	public Channel(final long id) {
		this.id = id;
	}

	public long getId() {
		return this.id;
	}

}
