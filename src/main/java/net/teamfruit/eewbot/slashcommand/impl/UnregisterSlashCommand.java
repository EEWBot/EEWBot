package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.util.Permission;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.slashcommand.IButtonSlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

public class UnregisterSlashCommand implements IButtonSlashCommand {

    @Override
    public String getCommandName() {
        return "unregister";
    }

    @Override
    public List<String> getCustomIds() {
        return List.of("unregister-confirm", "unregister-cancel");
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("このチャンネルの登録を解除します。")
                .defaultPermission(false)
                .defaultMemberPermissions(String.valueOf(Permission.MANAGE_GUILD.getValue()))
                .build();
    }

    @Override
    public Mono<Void> on(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        if (channel == null) {
            return event.reply(ctx.i18n().get(lang, "eewbot.scmd.unregister.notfound"))
                    .withEphemeral(true)
                    .then();
        }
        return event.reply(ctx.i18n().get(lang, "eewbot.scmd.unregister.confirm"))
                .withEphemeral(true)
                .withComponents(ActionRow.of(
                        Button.danger("unregister-confirm", ctx.i18n().get(lang, "eewbot.scmd.unregister.confirm.label")),
                        Button.secondary("unregister-cancel", ctx.i18n().get(lang, "eewbot.scmd.unregister.cancel.label"))
                ))
                .then();
    }

    @Override
    public Mono<Void> onClick(SlashCommandContext ctx, ButtonInteractionEvent event, String lang) {
        long targetId = event.getInteraction().getChannelId().asLong();
        if (event.getCustomId().equals("unregister-confirm")) {
            ctx.adminRegistry().remove(targetId);
            return Mono.create(sink -> {
                        try {
                            ctx.adminRegistry().save();
                            sink.success();
                        } catch (IOException e) {
                            sink.error(e);
                        }
                    })
                    .then(event.edit()
                            .withContent(ctx.i18n().get(lang, "eewbot.scmd.unregister.done"))
                            .withComponents())
                    .then();
        } else if (event.getCustomId().equals("unregister-cancel")) {
            return event.edit()
                    .withContent(ctx.i18n().get(lang, "eewbot.scmd.unregister.cancelled"))
                    .withComponents()
                    .then();
        }
        return Mono.empty();
    }
}
