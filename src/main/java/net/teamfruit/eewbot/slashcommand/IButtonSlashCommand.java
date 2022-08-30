package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IButtonSlashCommand extends ISlashCommand {

    List<String> getCustomIds();

    Mono<Void> onClick(EEWBot bot, ButtonInteractionEvent event, String lang);

}
