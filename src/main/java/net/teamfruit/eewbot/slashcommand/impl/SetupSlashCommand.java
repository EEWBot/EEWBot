package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
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
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event) {
        long channelId = event.getInteraction().getChannelId().asLong();
        if (!bot.getChannels().containsKey(channelId)) {
            bot.getChannelsLock().writeLock().lock();
            bot.getChannels().put(channelId, new Channel(false, false, false, false, false, false, SeismicIntensity.ONE));
            bot.getChannelsLock().writeLock().unlock();
        }

        return event.reply("このチャンネルのBotの動作を設定します。")
                .withComponents(ActionRow.of(buildMainSelectMenu(bot, channelId)), ActionRow.of(buildSensitivitySelectMenu(bot, channelId)))
                .withEphemeral(true);
    }

    private SelectMenu buildMainSelectMenu(EEWBot bot, long channelId) {
        Map<String, Boolean> fields = bot.getChannels().get(channelId).getCommandFields();
        return SelectMenu.of("channel", fields.entrySet().stream().map(entry -> {
                    if (entry.getValue())
                        return SelectMenu.Option.ofDefault(entry.getKey(), entry.getKey());
                    return SelectMenu.Option.of(entry.getKey(), entry.getKey());
                }).collect(Collectors.toList()))
                .withPlaceholder("メイン設定（複数選択可）")
                .withMinValues(0)
                .withMaxValues(fields.size());
    }

    private SelectMenu buildSensitivitySelectMenu(EEWBot bot, long channelId) {
        Channel channel = bot.getChannels().get(channelId);
        return SelectMenu.of("sensitivity", Arrays.stream(SeismicIntensity.values()).map(intensity -> {
                    if (channel.minIntensity == intensity)
                        return SelectMenu.Option.ofDefault("最小通知震度 " + intensity.getSimple(), intensity.getSimple());
                    return SelectMenu.Option.of("最小通知震度 " + intensity.getSimple(), intensity.getSimple());
                }).collect(Collectors.toList()))
                .withPlaceholder("最小通知震度")
                .withMinValues(1)
                .withMaxValues(1);
    }

    @Override
    public Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event) {
        if (event.getCustomId().equals("channel"))
            return event.deferReply().then(applyChannel(bot, event)).then();
        else if (event.getCustomId().equals("sensitivity"))
            return event.deferReply().then(applySensitivity(bot, event)).then();
        return Mono.empty();
    }

    private Mono<Message> applyChannel(EEWBot bot, SelectMenuInteractionEvent event) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        channel.getCommandFields().keySet().forEach(name -> channel.set(name, event.getValues().contains(name)));
        try {
            bot.getChannelRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        if (event.getValues().isEmpty())
            return event.createFollowup("すべての設定が解除されました！今後このチャンネルには何も送信されません。");
        return event.createFollowup("設定しました！今後このチャンネルに以下の情報が送信されます。\r" + String.join(", ", event.getValues()))
                .withEphemeral(true);
    }

    private Mono<Message> applySensitivity(EEWBot bot, SelectMenuInteractionEvent event) {
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
        return event.createFollowup("最小通知震度を設定しました！今後このチャンネルに**震度" + channel.minIntensity.getSimple() + "**以上の情報が送信されます。")
                .withEphemeral(true);
    }
}
