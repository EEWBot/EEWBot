package net.teamfruit.eewbot;

public class Config {
	private String token = "";
	private int kyoshinDelay = 1;
	private int quakeInfoDelay = 15;
	private int timeFixDelay = 86400;
	private String nptServer = "time.google.com";
	private boolean enablePermission = true;
	private boolean debug = false;

	public Config() {
	}

	public Config(final Config src) {
		set(src);
	}

	public Config set(final Config src) {
		this.token = src.getToken();
		this.kyoshinDelay = src.getKyoshinDelay();
		this.quakeInfoDelay = src.getQuakeInfoDelay();
		this.timeFixDelay = src.getTimeFixDelay();
		this.nptServer = src.getNptServer();
		this.enablePermission = src.isEnablePermission();
		this.debug = src.isDebug();
		return this;
	}

	public String getToken() {
		return this.token;
	}

	public int getKyoshinDelay() {
		return this.kyoshinDelay;
	}

	public int getQuakeInfoDelay() {
		return this.quakeInfoDelay;
	}

	public int getTimeFixDelay() {
		return this.timeFixDelay;
	}

	public String getNptServer() {
		return this.nptServer;
	}

	public boolean isEnablePermission() {
		return this.enablePermission;
	}

	public boolean isDebug() {
		return this.debug;
	}

	@Override
	public String toString() {
		return "Config [token="+this.token+", kyoshinDelay="+this.kyoshinDelay+", timeFixDelay="+this.timeFixDelay+", nptServer="+this.nptServer+"]";
	}

}
