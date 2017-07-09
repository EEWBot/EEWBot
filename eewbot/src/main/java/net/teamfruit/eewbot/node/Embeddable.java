package net.teamfruit.eewbot.node;

import sx.blah.discord.api.internal.json.objects.EmbedObject;

public interface Embeddable {

	EmbedObject buildEmbed();
}
