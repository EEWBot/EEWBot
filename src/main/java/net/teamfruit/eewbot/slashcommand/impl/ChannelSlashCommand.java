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
import java.util.stream.Collectors;

public class ChannelSlashCommand implements ISelectMenuSlashCommand {
    @Override
    public String getCommandName() {
        return "channel";
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

        return event.reply("このチャンネルの設定を選択してください。（複数選択可）").withComponents(ActionRow.of(buildSelectMenu(bot, channelId)));
    }

    private SelectMenu buildSelectMenu(EEWBot bot, long channelId) {
        Map<String, Boolean> fields = bot.getChannels().get(channelId).getCommandFields();
        return SelectMenu.of("channel", fields.entrySet().stream().map(entry -> {
                    if (entry.getValue())
                        return SelectMenu.Option.ofDefault(entry.getKey(), entry.getKey());
                    else
                        return SelectMenu.Option.of(entry.getKey(), entry.getKey());
                }).collect(Collectors.toList()))
                .withMinValues(0)
                .withMaxValues(fields.size());
    }

    @Override
    public Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event) {
        return event.deferReply().then(apply(bot, event)).then();
    }

    private Mono<Message> apply(EEWBot bot, SelectMenuInteractionEvent event) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        channel.getCommandFields().keySet().forEach(name -> channel.set(name, event.getValues().contains(name)));
        try {
            bot.getChannelRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        return event.createFollowup("設定しました！:" + event.getValues());
    }
}
