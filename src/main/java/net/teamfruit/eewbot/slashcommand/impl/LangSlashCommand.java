package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.IButtonSlashCommand;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import net.teamfruit.eewbot.slashcommand.SlashCommandUtils;
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
    public Mono<Void> on(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        long targetId = event.getInteraction().getChannelId().asLong();
        Mono<Void> ensureRegistered;
        if (channel == null) {
            Long guildId = event.getInteraction().getGuildId().map(Snowflake::asLong).orElse(null);
            ensureRegistered = event.getInteraction().getChannel()
                    .filter(GuildChannel.class::isInstance)
                    .cast(GuildChannel.class)
                    .doOnNext(ch -> SlashCommandUtils.createAndRegisterDefault(ctx.adminRegistry(), ch, targetId, guildId, lang))
                    .switchIfEmpty(Mono.fromRunnable(
                            () -> ctx.adminRegistry().put(targetId, Channel.createDefault(guildId, targetId, null, lang))))
                    .then();
        } else {
            ensureRegistered = Mono.empty();
        }
        return ensureRegistered
                .then(event.reply()
                        .withEphemeral(true)
                        .withComponents(buildActionRows(ctx, lang, event.getInteraction().getGuildId().isPresent())))
                .then(Mono.create(sink -> {
                    try {
                        ctx.adminRegistry().save();
                        sink.success();
                    } catch (IOException e) {
                        sink.error(e);
                    }
                }));
    }

    @Override
    public Mono<Void> onSelect(SlashCommandContext ctx, SelectMenuInteractionEvent event, String lang) {
        if (event.getCustomId().equals("lang-set")) {
            String langKey = event.getValues().getFirst();
            ctx.adminRegistry().setLang(event.getInteraction().getChannelId().asLong(), langKey);
            try {
                ctx.adminRegistry().save();
            } catch (IOException e) {
                return Mono.error(e);
            }
            return event.edit()
                    .withComponents(buildActionRows(ctx, langKey, event.getInteraction().getGuildId().isPresent()))
                    .then(event.createFollowup()
                            .withEphemeral(true)
                            .withContent(ctx.i18n().format(langKey, "eewbot.scmd.lang.set.followup", ctx.i18n().getLanguages().get(langKey))))
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> onClick(SlashCommandContext ctx, ButtonInteractionEvent event, String lang) {
        if (event.getCustomId().equals("lang-apply")) {
            Optional<Snowflake> guildId = event.getInteraction().getGuildId();
            if (guildId.isEmpty())
                return Mono.empty();
            ctx.adminRegistry().setLangByGuildId(guildId.get().asLong(), lang);
            try {
                ctx.adminRegistry().save();
            } catch (IOException e) {
                return Mono.error(e);
            }
            return event.createFollowup()
                    .withEphemeral(true)
                    .withContent(ctx.i18n().format(lang, "eewbot.scmd.lang.applyall.followup", ctx.i18n().getLanguages().get(lang)))
                    .then();
        }
        return Mono.empty();
    }

    private List<ActionRow> buildActionRows(SlashCommandContext ctx, String lang, boolean isGuild) {
        ActionRow selectMenu = ActionRow.of(SelectMenu.of("lang-set", ctx.i18n().getLanguages().entrySet().stream()
                        .map(entry -> {
                            if (entry.getKey().equals(lang))
                                return SelectMenu.Option.ofDefault(entry.getValue(), entry.getKey());
                            return SelectMenu.Option.of(entry.getValue(), entry.getKey());
                        })
                        .collect(Collectors.toList()))
                .withMinValues(1)
                .withMaxValues(1));
        ActionRow button = ActionRow.of(Button.primary("lang-apply", ctx.i18n().get(lang, "eewbot.scmd.lang.applyall.label")));
        if (isGuild)
            return List.of(selectMenu, button);
        return List.of(selectMenu);
    }
}
