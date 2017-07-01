package net.teamfruit.eewbot;

public class Channel {
	private final long id;
	public boolean eewAlert = true;
	public boolean eewPrediction = false;

	public Channel(final long id) {
		this.id = id;
	}

	public long getId() {
		return this.id;
	}

}
