package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.SelectMenu;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.registry.ChannelFilter;
import net.teamfruit.eewbot.slashcommand.IButtonSlashCommand;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LangSlashCommand implements ISelectMenuSlashCommand, IButtonSlashCommand {

    @Override
    public String getCommandName() {
        return "lang";
    }

    @Override
    public List<String> getCustomIds() {
        return List.of("lang-set", "lang-apply");
    }

    @Override
    public boolean isDeferOnClick() {
        return true;
    }

    @Override
    public boolean isEphemeralOnClickWhenDefer() {
        return true;
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
        bot.getChannels().computeIfAbsent(event.getInteraction().getChannelId().asLong(), key ->
                Channel.createDefault(event.getInteraction().getGuildId().map(Snowflake::asLong).orElse(null), lang));
        return event.reply()
                .withEphemeral(true)
                .withComponents(buildActionRows(bot, lang, event.getInteraction().getGuildId().isPresent()));
    }

    @Override
    public Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        if (event.getCustomId().equals("lang-set")) {
            String langKey = event.getValues().get(0);
            bot.getChannels().setLang(event.getInteraction().getChannelId().asLong(), langKey);
            try {
                bot.getChannels().save();
            } catch (IOException e) {
                return Mono.error(e);
            }
            return event.edit()
                    .withComponents(buildActionRows(bot, langKey, event.getInteraction().getGuildId().isPresent()))
                    .then(event.createFollowup()
                            .withEphemeral(true)
                            .withContent(bot.getI18n().format(langKey, "eewbot.scmd.lang.set.followup", bot.getI18n().getLanguages().get(langKey))))
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> onClick(EEWBot bot, ButtonInteractionEvent event, String lang) {
        if (event.getCustomId().equals("lang-apply")) {
            Optional<Snowflake> guildId = event.getInteraction().getGuildId();
            if (guildId.isEmpty())
                return Mono.empty();
            bot.getChannels().actionOnChannels(ChannelFilter.builder().guildId(guildId.get().asLong()).build(), channelId -> bot.getChannels().setLang(channelId, lang));
            try {
                bot.getChannels().save();
            } catch (IOException e) {
                return Mono.error(e);
            }
            return event.createFollowup()
                    .withEphemeral(true)
                    .withContent(bot.getI18n().format(lang, "eewbot.scmd.lang.applyall.followup", bot.getI18n().getLanguages().get(lang)))
                    .then();
        }
        return Mono.empty();
    }

    private List<ActionRow> buildActionRows(EEWBot bot, String lang, boolean isGuild) {
        ActionRow selectMenu = ActionRow.of(SelectMenu.of("lang-set", bot.getI18n().getLanguages().entrySet().stream()
                        .map(entry -> {
                            if (entry.getKey().equals(lang))
                                return SelectMenu.Option.ofDefault(entry.getValue(), entry.getKey());
                            return SelectMenu.Option.of(entry.getValue(), entry.getKey());
                        })
                        .collect(Collectors.toList()))
                .withMinValues(1)
                .withMaxValues(1));
        ActionRow button = ActionRow.of(Button.primary("lang-apply", bot.getI18n().get(lang, "eewbot.scmd.lang.applyall.label")));
        if (isGuild)
            return List.of(selectMenu, button);
        return List.of(selectMenu);
    }
}
