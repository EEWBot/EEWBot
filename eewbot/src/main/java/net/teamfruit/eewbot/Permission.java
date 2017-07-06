package net.teamfruit.eewbot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Permission {

	public static final Permission EVERYONE = new Permission("everyone", null, Arrays.asList("details", "monitor"));

	private String group;
	private List<Long> userid;
	private List<String> command;

	public Permission() {
	}

	public Permission(final String group, final List<Long> userid, final List<String> command) {
		this.group = group;
		this.userid = userid;
		this.command = command;
	}

	public String getGroup() {
		return this.group;
	}

	public List<Long> getUserid() {
		if (this.userid!=null)
			return this.userid;
		return this.userid = Collections.emptyList();
	}

	public List<String> getCommand() {
		if (this.command!=null)
			return this.command;
		return this.command = Collections.emptyList();
	}
}
