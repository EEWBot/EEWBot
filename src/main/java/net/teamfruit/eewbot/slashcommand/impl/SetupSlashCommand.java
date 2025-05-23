package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.WebhookCreateRequest;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.channel.Channel;
import net.teamfruit.eewbot.registry.channel.ChannelSetting;
import net.teamfruit.eewbot.registry.channel.ChannelSettingType;
import net.teamfruit.eewbot.registry.channel.ChannelWebhook;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SetupSlashCommand implements ISelectMenuSlashCommand {

    @Override
    public String getCommandName() {
        return "setup";
    }

    @Override
    public boolean isDefer() {
        return true;
    }

    @Override
    public boolean isEphemeralWhenDefer() {
        return true;
    }

    @Override
    public List<String> getCustomIds() {
        return Arrays.asList(ChannelSettingType.BASE.getCustomId(), ChannelSettingType.MODIFIER.getCustomId(), "sensitivity");
    }

    @Override
    public boolean isDeferOnSelect() {
        return true;
    }

    @Override
    public boolean isEphemeralOnSelectWhenDefer() {
        return true;
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
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        bot.getChannels().computeIfAbsent(channelId, key -> Channel.createDefault(event.getInteraction().getGuildId().map(Snowflake::asLong).orElse(null), lang));
        return Mono.justOrEmpty(event.getInteraction().getGuildId())
                .flatMap(guildId -> event.getInteraction().getChannel()
                        .filter(GuildChannel.class::isInstance)
                        .cast(GuildChannel.class)
                        .flatMap(guildChannel -> {
                            Mono<GuildChannel> permissionCheckChannel;
                            if (guildChannel instanceof ThreadChannel) {
                                permissionCheckChannel = guildChannel.getClient().getChannelById(((ThreadChannel) guildChannel).getParentId().orElseThrow())
                                        .cast(GuildChannel.class)
                                        .switchIfEmpty(Mono.just(guildChannel));
                            } else {
                                permissionCheckChannel = Mono.just(guildChannel);
                            }
                            return permissionCheckChannel.flatMap(target -> target.getEffectivePermissions(event.getClient().getSelfId())
                                            .filterWhen(perms -> perms.contains(Permission.SEND_MESSAGES) ? Mono.just(true)
                                                    : event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.setup.permserror.sendmessages")).thenReturn(false))
                                            .filterWhen(perms -> perms.contains(Permission.MANAGE_WEBHOOKS) ? Mono.just(true)
                                                    : buildReply(bot, event, lang, channelId, true).thenReturn(false)) // No webhook perm
                                            .flatMap(perms -> event.getInteraction().getGuild()
                                                    .flatMap(guild -> bot.getClient().getRestClient().getWebhookService().getGuildWebhooks(guildId.asLong())
                                                            .filter(webhook -> {
                                                                boolean isThreadChannel = guildChannel instanceof ThreadChannel;
                                                                long targetChannelId = isThreadChannel
                                                                        ? ((ThreadChannel) guildChannel).getParentId().map(Snowflake::asLong).orElseThrow()
                                                                        : channelId;
                                                                boolean isSameChannel = webhook.channelId().isPresent() && (webhook.channelId().get().asLong() == targetChannelId);
                                                                boolean isCreatedBySelf = webhook.user().toOptional()
                                                                        .filter(user -> user.id().asLong() == event.getClient().getSelfId().asLong())
                                                                        .isPresent();
                                                                return isThreadChannel
                                                                        ? isSameChannel && isCreatedBySelf && bot.getChannels().isWebhookForThread(webhook.id().asLong(), targetChannelId)
                                                                        : isSameChannel && isCreatedBySelf;
                                                            })
                                                            .next()
                                                            .switchIfEmpty(bot.getClient().getSelfMember(guildId).map(PartialMember::getDisplayName)
                                                                    .flatMap(name -> event.getClient().getRestClient().getWebhookService()
                                                                            .createWebhook(guildChannel instanceof ThreadChannel
                                                                                    ? ((ThreadChannel) guildChannel).getParentId().map(Snowflake::asLong).orElseThrow()
                                                                                    : channelId, WebhookCreateRequest.builder()
                                                                                    .name(removeUnusableName(name, bot.getUsername()))
                                                                                    .build(), "Create EEWBot webhook")))
                                                            .flatMap(webhookData -> Mono.fromRunnable(() -> {
                                                                ChannelWebhook webhook = new ChannelWebhook(webhookData.id().asLong(), webhookData.token().get(), guildChannel instanceof ThreadChannel ? channelId : null);
                                                                bot.getChannels().setWebhook(channelId, webhook);
                                                            })).then(buildReply(bot, event, lang, channelId, false))
                                                    )))
                                    .onErrorResume(ClientException.isStatusCode(403), err -> event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.setup.permserror.viewchannel")))
                                    .thenReturn(true);
                        })
                        .switchIfEmpty(buildReply(bot, event, lang, channelId, false).thenReturn(true)) // DM
                        .then(Mono.create(sink -> {
                            try {
                                bot.getChannels().save();
                                sink.success();
                            } catch (IOException e) {
                                sink.error(e);
                            }
                        })));
    }

    private Mono<Message> buildReply(EEWBot bot, ApplicationCommandInteractionEvent event, String lang, long channelId, boolean noWebhook) {
        return event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.setup.reply") + (noWebhook ? "\n\n" + bot.getI18n().get(lang, "eewbot.scmd.setup.permserror.managewebhooks") : ""))
                .withComponents(ActionRow.of(buildSelectMenu(bot, ChannelSettingType.BASE, channelId, lang)),
                        ActionRow.of(buildSelectMenu(bot, ChannelSettingType.MODIFIER, channelId, lang)),
                        ActionRow.of(buildSensitivitySelectMenu(bot, channelId, lang)));
    }

    private SelectMenu buildSelectMenu(EEWBot bot, ChannelSettingType type, long channelId, String lang) {
        Map<String, Boolean> fields = bot.getChannels().get(channelId).getSettingsByType(type);
        return SelectMenu.of(type.getCustomId(), fields.entrySet().stream().map(entry -> {
                    String label = bot.getI18n().get(lang, "eewbot.scmd.setup." + type.getCustomId() + "." + entry.getKey().toLowerCase() + ".label");
                    SelectMenu.Option option = entry.getValue() ? SelectMenu.Option.ofDefault(label, entry.getKey()) : SelectMenu.Option.of(label, entry.getKey());
                    option = option.withDescription(bot.getI18n().get(lang, "eewbot.scmd.setup." + type.getCustomId() + "." + entry.getKey().toLowerCase() + ".desc"));
                    return option;
                }).collect(Collectors.toList()))
                .withPlaceholder(bot.getI18n().get(lang, "eewbot.scmd.setup." + type.getCustomId() + ".placeholder"))
                .withMinValues(0)
                .withMaxValues(fields.size());
    }

    private SelectMenu buildSensitivitySelectMenu(EEWBot bot, long channelId, String lang) {
        Channel channel = bot.getChannels().get(channelId);
        return SelectMenu.of("sensitivity", Arrays.stream(SeismicIntensity.values()).map(intensity -> {
                    String label = intensity == SeismicIntensity.UNKNOWN ? bot.getI18n().get(lang, "eewbot.scmd.setup.sensitivity.option.unknown") : bot.getI18n().format(lang, "eewbot.scmd.setup.sensitivity.option", intensity);
                    if (channel.getMinIntensity() == intensity)
                        return SelectMenu.Option.ofDefault(label, intensity.getSimple());
                    return SelectMenu.Option.of(label, intensity.getSimple());
                }).collect(Collectors.toList()))
                .withPlaceholder(bot.getI18n().get(lang, "eewbot.scmd.setup.sensitivity.placeholder"))
                .withMinValues(1)
                .withMaxValues(1);
    }

    @Override
    public Mono<Void> onSelect(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        if (ChannelSettingType.hasCustomId(event.getCustomId()))
            return applyChannel(bot, event, lang).then();
        else if (event.getCustomId().equals("sensitivity"))
            return applySensitivity(bot, event, lang).then();
        return Mono.empty();
    }

    private Mono<Message> applyChannel(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        Arrays.stream(Channel.class.getDeclaredFields())
                .filter(field -> {
                    if (!field.isAnnotationPresent(ChannelSetting.class))
                        return false;
                    ChannelSetting annotation = field.getAnnotation(ChannelSetting.class);
                    return annotation != null && annotation.value().getCustomId().equals(event.getCustomId());
                })
                .map(Field::getName)
                .forEach(name -> bot.getChannels().set(channelId, name, event.getValues().contains(name)));
        try {
            bot.getChannels().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        if (event.getValues().isEmpty())
            return event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.setup." + event.getCustomId() + ".followup.none"));
        return event.createFollowup(bot.getI18n().format(lang, "eewbot.scmd.setup." + event.getCustomId() + ".followup.any",
                event.getValues().stream()
                        .map(value -> Channel.toI18nKey(value)
                                .map(key -> bot.getI18n().format(lang, key))
                                .orElse(""))
                        .collect(Collectors.joining(", "))));
    }

    private Mono<Message> applySensitivity(EEWBot bot, SelectMenuInteractionEvent event, String lang) {
        long channelId = event.getInteraction().getChannelId().asLong();
        SeismicIntensity intensity = SeismicIntensity.get(event.getValues().get(0));
        bot.getChannels().setMinIntensity(channelId, intensity);
        try {
            bot.getChannels().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        return event.createFollowup(bot.getI18n().format(lang, intensity != SeismicIntensity.UNKNOWN ? "eewbot.scmd.setup.sensitivity.followup" : "eewbot.scmd.setup.sensitivity.followup.unknown", intensity.getSimple()));
    }

    public static String removeStringsIgnoreCase(String source, String... stringsToRemove) {
        for (String remove : stringsToRemove) {
            source = StringUtils.removeIgnoreCase(source, remove);
        }
        return source;
    }

    private static String removeUnusableName(String name, String fallbackName) {
        String removed = removeStringsIgnoreCase(name, "discord", "clyde");
        if (StringUtils.isNotBlank(removed))
            return removed;
        removed = removeStringsIgnoreCase(fallbackName, "discord", "clyde");
        if (StringUtils.isNotBlank(removed))
            return removed;
        return "EEWBot";
    }
}
