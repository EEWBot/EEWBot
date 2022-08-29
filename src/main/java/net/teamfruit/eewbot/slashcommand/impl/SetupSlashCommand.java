package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.Channel;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SetupSlashCommand implements ISelectMenuSlashCommand {
    @Override
    public String getCommandName() {
        return "setup";
    }

    @Override
    public List<String> getCustomIds() {
        return Arrays.asList("channel", "sensitivity");
    }

    @Override
    public ApplicationCommandRequest buildCommand() {
        return ApplicationCommandRequest.builder()
                .name(getCommandName())
                .description("現在のチャンネルにBotが通知する情報を設定します。")
                .build();
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        if (!bot.getChannels().containsKey(channelId)) {
            bot.getChannelsLock().writeLock().lock();
            bot.getChannels().put(channelId, new Channel(false, false, false, false, false, false, SeismicIntensity.ONE));
            bot.getChannelsLock().writeLock().unlock();
        }

        return event.reply(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.reply"))
                .withComponents(ActionRow.of(buildMainSelectMenu(bot, channelId, lang)), ActionRow.of(buildSensitivitySelectMenu(bot, channelId, lang)))
                .withEphemeral(true);
    }

    private SelectMenu buildMainSelectMenu(EEWBot bot, long channelId, String lang) {
        Map<String, Boolean> fields = bot.getChannels().get(channelId).getCommandFields();
        return SelectMenu.of("channel", fields.entrySet().stream().map(entry -> {
                    if (entry.getValue())
                        return SelectMenu.Option.ofDefault(entry.getKey(), entry.getKey());
                    return SelectMenu.Option.of(entry.getKey(), entry.getKey());
                }).collect(Collectors.toList()))
                .withPlaceholder(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel.placeholder"))
                .withMinValues(0)
                .withMaxValues(fields.size());
    }

    private SelectMenu buildSensitivitySelectMenu(EEWBot bot, long channelId, String lang) {
        Channel channel = bot.getChannels().get(channelId);
        return SelectMenu.of("sensitivity", Arrays.stream(SeismicIntensity.values()).map(intensity -> {
                    if (channel.minIntensity == intensity)
                        return SelectMenu.Option.ofDefault(I18n.INSTANCE.format(lang, "eewbot.scmd.setup.sensitivity.option", intensity), intensity.getSimple());
                    return SelectMenu.Option.of(I18n.INSTANCE.format(lang, "eewbot.scmd.setup.sensitivity.option", intensity), intensity.getSimple());
                }).collect(Collectors.toList()))
                .withPlaceholder(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.sensitivity.placeholder"))
                .withMinValues(1)
                .withMaxValues(1);
    }

    @Override
    public Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        if (event.getCustomId().equals("channel"))
            return event.deferReply().then(applyChannel(bot, event, lang)).then();
        else if (event.getCustomId().equals("sensitivity"))
            return event.deferReply().then(applySensitivity(bot, event, lang)).then();
        return Mono.empty();
    }

    private Mono<Message> applyChannel(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        channel.getCommandFields().keySet().forEach(name -> channel.set(name, event.getValues().contains(name)));
        try {
            bot.getChannelRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        if (event.getValues().isEmpty())
            return event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel.followup.none"));
        return event.createFollowup(I18n.INSTANCE.format(lang, "eewbot.scmd.setup.channel.followup.any", String.join(", ", event.getValues())))
                .withEphemeral(true);
    }

    private Mono<Message> applySensitivity(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        Optional<SeismicIntensity> intensity = SeismicIntensity.get(event.getValues().get(0));
        if (!intensity.isPresent())
            return Mono.empty();
        channel.minIntensity = intensity.get();
        try {
            bot.getChannelRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        return event.createFollowup(I18n.INSTANCE.format(lang, "eewbot.scmd.setup.sensitivity.followup", channel.minIntensity.getSimple()))
                .withEphemeral(true);
    }
}
