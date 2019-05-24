package net.teamfruit.eewbot.registry;

public class Config {
	private String token = "";
	private int kyoshinDelay = 1;
	private int quakeInfoDelay = 15;
	private String nptServer = "time.google.com";
	private String defaultLanuage = "ja_jp";
	private boolean enablePermission = true;
	private boolean debug = false;

	public Config() {
	}

	public String getToken() {
		return this.token;
	}

	public void setToken(final String token) {
		this.token = token;
	}

	public int getKyoshinDelay() {
		return this.kyoshinDelay>=1 ? this.kyoshinDelay : 1;
	}

	public void setKyoshinDelay(final int kyoshinDelay) {
		this.kyoshinDelay = kyoshinDelay;
	}

	public int getQuakeInfoDelay() {
		return this.quakeInfoDelay>=10 ? this.quakeInfoDelay : 10;
	}

	public void setQuakeInfoDelay(final int quakeInfoDelay) {
		this.quakeInfoDelay = quakeInfoDelay;
	}

	public String getNptServer() {
		return this.nptServer;
	}

	public void setNptServer(final String nptServer) {
		this.nptServer = nptServer;
	}

	public String getDefaultLanuage() {
		return this.defaultLanuage;
	}

	public void setDefaultLanuage(final String defaultLanuage) {
		this.defaultLanuage = defaultLanuage;
	}

	public boolean isEnablePermission() {
		return this.enablePermission;
	}

	public void setEnablePermission(final boolean enablePermission) {
		this.enablePermission = enablePermission;
	}

	public boolean isDebug() {
		return this.debug;
	}

	public void setDebug(final boolean debug) {
		this.debug = debug;
	}

	@Override
	public String toString() {
		return "Config [token="+this.token+", kyoshinDelay="+this.kyoshinDelay+", quakeInfoDelay="+this.quakeInfoDelay+", nptServer="+this.nptServer+", enablePermission="+this.enablePermission+", debug="+this.debug+"]";
	}

}
