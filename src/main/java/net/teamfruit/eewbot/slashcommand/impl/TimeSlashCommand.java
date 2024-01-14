package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.TimeProvider;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.IButtonSlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandUtils;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

public class TimeSlashCommand implements IButtonSlashCommand {
    @Override
    public String getCommandName() {
        return "time";
    }

    @Override
    public List<String> getCustomIds() {
        return Collections.singletonList("timesync");
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("Botの時刻同期を確認します。")
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        return event.reply().withEmbeds(buildTimeEmbed(bot.getExecutor().getTimeProvider(), lang))
                .withComponents(ActionRow.of(Button.primary("timesync", bot.getI18n().get(lang, "eewbot.scmd.time.resync"))
                        .disabled(!bot.getConfig().isEnableKyoshin())));
    }

    @Override
    public Mono<Void> onClick(EEWBot bot, ButtonInteractionEvent event, String lang) {
        return event.deferEdit().then(resync(bot, event, lang).then());
    }

    private Mono<Message> resync(EEWBot bot, ButtonInteractionEvent event, String lang) {
        return bot.getExecutor().getTimeProvider().fetch()
                .flatMap(time -> event.editReply()
                        .withEmbeds(buildTimeEmbed(time, lang))
                        .withComponents(ActionRow.of(Button.primary("timesync", bot.getI18n().get(lang, "eewbot.scmd.time.resync")).disabled())));
    }

    private EmbedCreateSpec buildTimeEmbed(TimeProvider time, String lang) {
        if (time.isProviding())
            return SlashCommandUtils.createEmbed(lang)
                    .title("eewbot.scmd.time.title")
                    .addField("eewbot.scmd.time.field.lastpctime.name", time.getLastComputerTime().toString(), false)
                    .addField("eewbot.scmd.time.field.lastntptime.name", time.getLastNTPTime().toString(), false)
                    .addField("eewbot.scmd.time.field.nowpctime.name", ZonedDateTime.now(TimeProvider.ZONE_ID).toString(), false)
                    .addField("eewbot.scmd.time.field.nowoffsettime.name", time.now().toString(), false)
                    .addField("eewbot.scmd.time.field.offset.name", String.valueOf(time.getOffset()), false)
                    .build();
        return SlashCommandUtils.createEmbed(lang)
                .title("eewbot.scmd.time.title")
                .addField("eewbot.scmd.time.field.lastpctime.name", "eewbot.scmd.time.field.nonsync.value", false)
                .addField("eewbot.scmd.time.field.lastntptime.name", "eewbot.scmd.time.field.nonsync.value", false)
                .addField("eewbot.scmd.time.field.nowpctime.name", ZonedDateTime.now(TimeProvider.ZONE_ID).toString(), false)
                .addField("eewbot.scmd.time.field.nowoffsettime.name", time.now().toString(), false)
                .addField("eewbot.scmd.time.field.offset.name", String.valueOf(time.getOffset()), false)
                .build();
    }
}
