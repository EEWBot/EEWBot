package net.teamfruit.eewbot;

public class Config {
	public String token;
	public int kyoshinDelay;
	public int timeFixDelay;

	public static Config getDefault() {
		final Config c = new Config();
		c.token = "";
		c.kyoshinDelay = 1;
		c.timeFixDelay = 86400;
		return c;
	}

}
