package net.teamfruit.eewbot.slashcommand;

import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import net.teamfruit.eewbot.EEWBot;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ISelectMenuSlashCommand extends ISlashCommand {

    List<String> getCustomIds();

    default boolean isDeferOnSelect() {
        return false;
    }

    default boolean isEphemeralOnSelectWhenDefer() {
        return false;
    }

    Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event, String lang);
}
