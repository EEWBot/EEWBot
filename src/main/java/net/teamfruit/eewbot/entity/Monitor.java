package net.teamfruit.eewbot.entity;

import java.io.ByteArrayInputStream;
import java.util.function.Consumer;

import discord4j.core.spec.MessageCreateSpec;

public class Monitor implements Entity {

	private final byte[] image;
	private final EEW relationEEW;

	public Monitor(final byte[] image, final EEW relationEEW) {
		this.image = image;
		this.relationEEW = relationEEW;
	}

	public Monitor(final byte[] image) {
		this(image, null);
	}

	public byte[] getImage() {
		return this.image;
	}

	public EEW getRelationEEW() {
		return this.relationEEW;
	}

	@Override
	public Consumer<? super MessageCreateSpec> createMessage() {
		return msg -> msg.addFile("kyoshinmonitor.png", new ByteArrayInputStream(getImage()));
	}

}
