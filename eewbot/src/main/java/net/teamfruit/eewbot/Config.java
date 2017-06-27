package net.teamfruit.eewbot;

public class Config {
	private String token;
	private int kyoshinDelay;
	private int timeFixDelay;
	private String nptServer;
	private boolean debug;

	public Config() {
		this.token = "";
		this.kyoshinDelay = 2;
		this.timeFixDelay = 86400;
		this.nptServer = "time.google.com";
		this.debug = false;
	}

	public Config(final Config src) {
		set(src);
	}

	public Config set(final Config src) {
		this.token = src.getToken();
		this.kyoshinDelay = src.getKyoshinDelay();
		this.timeFixDelay = src.getTimeFixDelay();
		this.nptServer = src.getNptServer();
		this.debug = src.isDebug();
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

	public boolean isDebug() {
		return this.debug;
	}

	@Override
	public String toString() {
		return "Config [token="+this.token+", kyoshinDelay="+this.kyoshinDelay+", timeFixDelay="+this.timeFixDelay+", nptServer="+this.nptServer+"]";
	}

}
