package net.teamfruit.eewbot.entity;

import discord4j.core.spec.MessageCreateFields;
import discord4j.core.spec.MessageCreateSpec;

import java.io.ByteArrayInputStream;

public class Monitor implements Entity {

    private final byte[] image;
    private final KmoniEEW relationEEW;

    public Monitor(final byte[] image, final KmoniEEW relationEEW) {
        this.image = image;
        this.relationEEW = relationEEW;
    }

    public Monitor(final byte[] image) {
        this(image, null);
    }

    public byte[] getImage() {
        return this.image;
    }

    public KmoniEEW getRelationEEW() {
        return this.relationEEW;
    }

    @Override
    public MessageCreateSpec createMessage(final String lang) {
        return MessageCreateSpec.builder().addFile("kyoshinmonitor.png", new ByteArrayInputStream(getImage())).build();
    }

    @Override
    public DiscordWebhook createWebhook(final String lang) {
        throw new UnsupportedOperationException();
    }

    public MessageCreateFields.File getFile() {
        return MessageCreateFields.File.of("kyoshinmonitor.png", new ByteArrayInputStream(getImage()));
    }

}
