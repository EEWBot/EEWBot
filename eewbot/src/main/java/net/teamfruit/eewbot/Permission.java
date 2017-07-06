package net.teamfruit.eewbot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Permission {

	public static final Permission EVERYONE = new Permission();

	private final String group = "everyone";
	private List<Long> userid;
	private List<String> command;

	public String getGroup() {
		return this.group;
	}

	public List<Long> getUserid() {
		if (this.userid!=null)
			return this.userid;
		return this.userid = new ArrayList<>();
	}

	public List<String> getCommand() {
		if (this.command!=null)
			return this.command;
		return this.command = Stream.generate(()->);
	}

}
