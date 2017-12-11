package net.teamfruit.eewbot;

import org.slf4j.Logger;

import sx.blah.discord.Discord4J.Discord4JLogger;

public class Log {
	public static Logger logger = new Discord4JLogger(EEWBot.class.getName());

}
