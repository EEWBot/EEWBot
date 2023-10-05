package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
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
                .defaultPermission(false)
                .defaultMemberPermissions(String.valueOf(Permission.MANAGE_GUILD.getValue()))
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

        return event.deferReply().withEphemeral(true).then(event.getInteraction().getChannel()
                .flatMap(channel -> Mono.just(channel)
                        .filter(GuildChannel.class::isInstance)
                        .cast(GuildChannel.class)
                        .flatMap(guildChannel -> guildChannel.getEffectivePermissions(bot.getClient().getSelfId()))
                        .filter(perms -> !perms.contains(Permission.VIEW_CHANNEL) || !perms.contains(Permission.SEND_MESSAGES))
                        .flatMap(perms -> {
                            if (!perms.contains(Permission.VIEW_CHANNEL))
                                return event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.permserror.viewchannel")).withEphemeral(true);
                            return event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.permserror.sendmessages")).withEphemeral(true);
                        }))
                .onErrorResume(ClientException.isStatusCode(403), err -> event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.permserror.viewchannel"))
                        .withEphemeral(true))
                .switchIfEmpty(event.createFollowup(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.reply"))
                        .withComponents(ActionRow.of(buildMainSelectMenu(bot, channelId, lang)), ActionRow.of(buildSensitivitySelectMenu(bot, channelId, lang)))
                        .withEphemeral(true)
                )).then();
    }

    private SelectMenu buildMainSelectMenu(EEWBot bot, long channelId, String lang) {
        Map<String, Boolean> fields = bot.getChannels().get(channelId).getCommandFields();
        return SelectMenu.of("channel", fields.entrySet().stream().map(entry -> {
                    String label = I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel." + entry.getKey().toLowerCase() + ".label");
                    SelectMenu.Option option = entry.getValue() ? SelectMenu.Option.ofDefault(label, entry.getKey()) : SelectMenu.Option.of(label, entry.getKey());
                    option = option.withDescription(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel." + entry.getKey().toLowerCase() + ".desc"));
                    return option;
                }).collect(Collectors.toList()))
                .withPlaceholder(I18n.INSTANCE.get(lang, "eewbot.scmd.setup.channel.placeholder"))
                .withMinValues(0)
                .withMaxValues(fields.size());
    }

    private SelectMenu buildSensitivitySelectMenu(EEWBot bot, long channelId, String lang) {
        Channel channel = bot.getChannels().get(channelId);
        return SelectMenu.of("sensitivity", Arrays.stream(SeismicIntensity.values()).map(intensity -> {
                    String label = intensity == SeismicIntensity.UNKNOWN ? I18n.INSTANCE.get(lang, "eewbot.scmd.setup.sensitivity.option.unknown") : I18n.INSTANCE.format(lang, "eewbot.scmd.setup.sensitivity.option", intensity);
                    if (channel.minIntensity == intensity)
                        return SelectMenu.Option.ofDefault(label, intensity.getSimple());
                    return SelectMenu.Option.of(label, intensity.getSimple());
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
        return event.createFollowup(I18n.INSTANCE.format(lang, "eewbot.scmd.setup.channel.followup.any",
                        event.getValues().stream()
                                .map(value -> Channel.toCommandName(value).orElse("")).collect(Collectors.joining(", "))))
                .withEphemeral(true);
    }

    private Mono<Message> applySensitivity(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        Channel channel = bot.getChannels().get(event.getInteraction().getChannelId().asLong());
        Optional<SeismicIntensity> intensity = SeismicIntensity.get(event.getValues().get(0));
        if (intensity.isEmpty())
            return Mono.empty();
        channel.minIntensity = intensity.get();
        try {
            bot.getChannelRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        return event.createFollowup(I18n.INSTANCE.format(lang, channel.minIntensity != SeismicIntensity.UNKNOWN ? "eewbot.scmd.setup.sensitivity.followup" : "eewbot.scmd.setup.sensitivity.followup.unknown", channel.minIntensity.getSimple()))
                .withEphemeral(true);
    }
}
