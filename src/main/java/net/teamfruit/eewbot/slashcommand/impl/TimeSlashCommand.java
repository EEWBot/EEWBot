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
import net.teamfruit.eewbot.command.CommandUtils;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.slashcommand.IButtonSlashCommand;
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
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang) {
        return event.reply().withEmbeds(buildTimeEmbed(bot, lang))
                .withComponents(ActionRow.of(Button.primary("timesync", I18n.INSTANCE.get(lang, "eewbot.scmd.time.rsync"))));
    }


    @Override
    public Mono<Void> onClick(EEWBot bot, ButtonInteractionEvent event, String lang) {
        return event.deferEdit().then(resync(bot, event, lang).then());
    }

    private Mono<Message> resync(EEWBot bot, ButtonInteractionEvent event, String lang) {
        return bot.getExecutor().getProvider().fetch().then(event.editReply()
                .withEmbeds(buildTimeEmbed(bot, lang))
                .withComponents(ActionRow.of(Button.primary("timesync", I18n.INSTANCE.get(lang, "eewbot.scmd.time.rsync")).disabled())));
    }

    private EmbedCreateSpec buildTimeEmbed(EEWBot bot, String lang) {
        return CommandUtils.createEmbed(lang)
                .title("eewbot.cmd.time.title")
                .addField("eewbot.cmd.time.field.lastpctime.name", bot.getExecutor().getProvider().getLastComputerTime()
                        .map(ZonedDateTime::toString)
                        .orElse("eewbot.cmd.time.field.nonsync.value"), false)
                .addField("eewbot.cmd.time.field.lastntptime.name", bot.getExecutor().getProvider().getLastNTPTime()
                        .map(ZonedDateTime::toString)
                        .orElse("eewbot.cmd.time.field.nonsync.value"), false)
                .addField("eewbot.cmd.time.field.nowpctime.name", ZonedDateTime.now(TimeProvider.ZONE_ID).toString(), false)
                .addField("eewbot.cmd.time.field.nowoffsettime.name", bot.getExecutor().getProvider().now().toString(), false)
                .addField("eewbot.cmd.time.field.offset.name", String.valueOf(bot.getExecutor().getProvider().getOffset()), false)
                .build();
    }
}
