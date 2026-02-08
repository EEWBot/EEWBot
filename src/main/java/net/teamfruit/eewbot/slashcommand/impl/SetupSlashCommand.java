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
import discord4j.discordjson.json.WebhookData;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelSetting;
import net.teamfruit.eewbot.registry.destination.model.ChannelSettingType;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    /**
     * Validates a webhook URL by sending an HTTP HEAD request.
     * Returns true if the webhook is valid (non-404), false if 404 (deleted).
     * On network errors, assumes valid to avoid unnecessary recreation.
     */
    private Mono<Boolean> validateWebhookUrl(EEWBot bot, ChannelWebhook webhook) {
        return Mono.fromCallable(() -> {
            // Use base URL without thread_id query parameter
            String baseUrl = ChannelWebhook.of(webhook.id(), webhook.token()).url();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = bot.getHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() != 404;
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    Log.logger.warn("Failed to validate webhook URL, assuming valid", e);
                    return Mono.just(true);
                });
    }

    /**
     * Finds an existing bot-created webhook matching the channel/thread, or creates a new one.
     */
    private Mono<WebhookData> findOrCreateWebhook(EEWBot bot, ApplicationCommandInteractionEvent event, Snowflake guildId, GuildChannel guildChannel, long targetId) {
        boolean isThreadChannel = guildChannel instanceof ThreadChannel;
        long parentChannelId = isThreadChannel
                ? ((ThreadChannel) guildChannel).getParentId().map(Snowflake::asLong).orElseThrow()
                : targetId;

        return bot.getClient().getRestClient().getWebhookService().getGuildWebhooks(guildId.asLong())
                .filter(webhook -> {
                    boolean isSameChannel = webhook.channelId().isPresent() && (webhook.channelId().get().asLong() == parentChannelId);
                    boolean isCreatedBySelf = webhook.user().toOptional()
                            .filter(user -> user.id().asLong() == event.getClient().getSelfId().asLong())
                            .isPresent();
                    return isSameChannel && isCreatedBySelf && bot.getAdminRegistry().isWebhookForThread(webhook.id().asLong(), targetId);
                })
                .next()
                .switchIfEmpty(bot.getClient().getSelfMember(guildId).map(PartialMember::getDisplayName)
                        .flatMap(name -> event.getClient().getRestClient().getWebhookService()
                                .createWebhook(parentChannelId, WebhookCreateRequest.builder()
                                        .name(removeUnusableName(name, bot.getUsername()))
                                        .build(), "Create EEWBot webhook")));
    }

    /**
     * Registers a webhook for a target by converting WebhookData to ChannelWebhook and storing it.
     */
    private void registerWebhook(EEWBot bot, GuildChannel guildChannel, long targetId, WebhookData webhookData) {
        Long threadId = guildChannel instanceof ThreadChannel ? targetId : null;
        ChannelWebhook webhook = ChannelWebhook.of(
                webhookData.id().asLong(),
                webhookData.token().get(),
                threadId
        );
        bot.getAdminRegistry().setWebhook(targetId, webhook);
    }

    /**
     * Checks MANAGE_WEBHOOKS permission; if present, finds/creates webhook and registers it.
     * If permission missing, shows reply with webhook warning.
     */
    private Mono<Message> createWebhookWithPermCheck(EEWBot bot, ApplicationCommandInteractionEvent event, Snowflake guildId, GuildChannel guildChannel, long targetId, PermissionSet perms, String lang) {
        if (!perms.contains(Permission.MANAGE_WEBHOOKS)) {
            return buildReply(bot, event, lang, targetId, true);
        }
        return findOrCreateWebhook(bot, event, guildId, guildChannel, targetId)
                .flatMap(webhookData -> {
                    registerWebhook(bot, guildChannel, targetId, webhookData);
                    return buildReply(bot, event, lang, targetId, false);
                });
    }

    @Override
    public Mono<Void> on(EEWBot bot, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
        // channel id or thread id
        long targetId = event.getInteraction().getChannelId().asLong();
        Long guildId = event.getInteraction().getGuildId().map(Snowflake::asLong).orElse(null);

        return Mono.justOrEmpty(event.getInteraction().getGuildId())
                .flatMap(gid -> event.getInteraction().getChannel()
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
                                            .flatMap(perms -> {
                                                if (channel == null) {
                                                    // Case 1: Not registered — register target and create webhook
                                                    Channel newChannel;
                                                    if (guildChannel instanceof ThreadChannel) {
                                                        Long channelId = ((ThreadChannel) guildChannel).getParentId().map(Snowflake::asLong).orElse(targetId);
                                                        newChannel = Channel.createDefault(guildId, channelId, targetId, lang);
                                                    } else {
                                                        newChannel = Channel.createDefault(guildId, targetId, null, lang);
                                                    }
                                                    bot.getAdminRegistry().put(targetId, newChannel);
                                                    return createWebhookWithPermCheck(bot, event, gid, guildChannel, targetId, perms, lang);
                                                } else if (channel.getWebhook() == null) {
                                                    // Case 2: Registered, no webhook — try to create webhook
                                                    return createWebhookWithPermCheck(bot, event, gid, guildChannel, targetId, perms, lang);
                                                } else {
                                                    // Case 3: Registered, has webhook — validate with HEAD request
                                                    return validateWebhookUrl(bot, channel.getWebhook())
                                                            .flatMap(valid -> {
                                                                if (valid) {
                                                                    // Webhook is valid, just show the menu
                                                                    return buildReply(bot, event, lang, targetId, false);
                                                                } else {
                                                                    // Webhook returned 404, recreate it
                                                                    return createWebhookWithPermCheck(bot, event, gid, guildChannel, targetId, perms, lang);
                                                                }
                                                            });
                                                }
                                            }))
                                    .onErrorResume(ClientException.isStatusCode(403), err -> event.createFollowup(bot.getI18n().get(lang, "eewbot.scmd.setup.permserror.viewchannel")))
                                    .thenReturn(true);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            // DM
                            if (channel == null) {
                                bot.getAdminRegistry().put(targetId, Channel.createDefault(guildId, targetId, null, lang));
                            }
                            return buildReply(bot, event, lang, targetId, false).thenReturn(true);
                        }))
                        .then(Mono.create(sink -> {
                            try {
                                bot.getAdminRegistry().save();
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
        Map<String, Boolean> fields = bot.getAdminRegistry().get(channelId).getSettingsByType(type);
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
        Channel channel = bot.getAdminRegistry().get(channelId);
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
        long targetId = event.getInteraction().getChannelId().asLong();
        Arrays.stream(Channel.class.getDeclaredFields())
                .filter(field -> {
                    if (!field.isAnnotationPresent(ChannelSetting.class))
                        return false;
                    ChannelSetting annotation = field.getAnnotation(ChannelSetting.class);
                    return annotation != null && annotation.value().getCustomId().equals(event.getCustomId());
                })
                .map(Field::getName)
                .forEach(name -> bot.getAdminRegistry().set(targetId, name, event.getValues().contains(name)));
        try {
            bot.getAdminRegistry().save();
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
        long targetId = event.getInteraction().getChannelId().asLong();
        SeismicIntensity intensity = SeismicIntensity.get(event.getValues().get(0));
        bot.getAdminRegistry().setMinIntensity(targetId, intensity);
        try {
            bot.getAdminRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        return event.createFollowup(bot.getI18n().format(lang, intensity != SeismicIntensity.UNKNOWN ? "eewbot.scmd.setup.sensitivity.followup" : "eewbot.scmd.setup.sensitivity.followup.unknown", intensity.getSimple()));
    }

    public static String removeStringsIgnoreCase(String source, String... stringsToRemove) {
        for (String remove : stringsToRemove) {
            source = Strings.CI.remove(source, remove);
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
