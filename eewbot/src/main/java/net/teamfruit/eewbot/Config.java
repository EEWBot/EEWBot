package net.teamfruit.eewbot;

public class Config {
	private String token;
	private int kyoshinDelay;
	private int timeFixDelay;
	private String nptServer;

	public Config() {
		this.token = "";
		this.kyoshinDelay = 2;
		this.timeFixDelay = 86400;
		this.nptServer = "time.google.com";

	}

	public Config(final Config src) {
		set(src);
	}

	public Config set(final Config src) {
		this.token = src.token;
		this.kyoshinDelay = src.kyoshinDelay;
		this.timeFixDelay = src.timeFixDelay;
		this.nptServer = src.nptServer;
		return this;
	}

	public String getToken() {
		return this.token;
	}

	public int getKyoshinDelay() {
		return this.kyoshinDelay;
	}

	public int getTimeFixDelay() {
		return this.timeFixDelay;
	}

	public String getNptServer() {
		return this.nptServer;
	}

	@Override
	public String toString() {
		return "Config [token="+this.token+", kyoshinDelay="+this.kyoshinDelay+", timeFixDelay="+this.timeFixDelay+", nptServer="+this.nptServer+"]";
	}

}
