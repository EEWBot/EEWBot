package net.teamfruit.eewbot;

import java.util.ArrayList;
import java.util.List;

public class Config {

	public String token;
	public int kyoshinDelay;
	public int timeFixDelay;
	public List<Channel> channels;

	public static Config getDefault() {
		final Config c = new Config();
		c.token = "";
		c.kyoshinDelay = 1;
		c.timeFixDelay = 86400;
		c.channels = new ArrayList<>();
		return c;
	}

}
