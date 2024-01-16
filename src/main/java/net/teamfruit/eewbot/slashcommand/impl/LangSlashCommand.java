package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class LangSlashCommand implements ISelectMenuSlashCommand {

    @Override
    public String getCommandName() {
        return "lang";
    }

    @Override
    public List<String> getCustomIds() {
        return List.of("lang");
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("チャンネルに言語を設定します。")
                .defaultPermission(false)
                .defaultMemberPermissions(String.valueOf(Permission.MANAGE_GUILD.getValue()))
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        bot.getChannels().computeIfAbsent(event.getInteraction().getChannelId().asLong(), key -> Channel.createDefault(lang));
        return event.reply()
                .withEphemeral(true)
                .withComponents(ActionRow.of(SelectMenu.of("lang", bot.getI18n().getLanguages().entrySet().stream()
                                .map(entry -> {
                                    if (entry.getKey().equals(lang))
                                        return SelectMenu.Option.ofDefault(entry.getValue(), entry.getKey());
                                    return SelectMenu.Option.of(entry.getValue(), entry.getKey());
                                })
                                .collect(Collectors.toList()))
                        .withMinValues(1)
                        .withMaxValues(1)));
    }

    @Override
    public Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        if (event.getCustomId().equals("lang")) {
            String langKey = event.getValues().get(0);
            bot.getChannels().setLang(event.getInteraction().getChannelId().asLong(), langKey);
            try {
                bot.getChannels().save();
            } catch (IOException e) {
                return Mono.error(e);
            }
            return event.reply()
                    .withEphemeral(true)
                    .withContent(bot.getI18n().format(langKey, "eewbot.scmd.lang.set", bot.getI18n().getLanguages().get(langKey)))
                    .then();
        }
        return Mono.empty();
    }
}
