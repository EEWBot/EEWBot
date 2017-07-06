package net.teamfruit.eewbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
		return this.command = Arrays.asList("details", "monitor");
	}

}
