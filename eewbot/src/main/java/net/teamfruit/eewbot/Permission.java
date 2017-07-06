package net.teamfruit.eewbot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Permission {

	public static final Permission EVERYONE = new Permission(Collections.emptyList(), Arrays.asList("details", "monitor", "help"));

	private List<Long> userid;
	private List<String> command;

	public Permission() {
	}

	public Permission(final List<Long> userid, final List<String> command) {
		this.userid = userid;
		this.command = command;
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
