package net.teamfruit.eewbot;

public class Config {
	public String token;
	public int kyoshinDelay;
	public int timeFixDelay;
	public String nptServer;

	public static Config getDefault() {
		final Config c = new Config();
		c.token = "";
		c.kyoshinDelay = 2;
		c.timeFixDelay = 86400;
		c.nptServer = "time.google.com";
		return c;
	}

}
