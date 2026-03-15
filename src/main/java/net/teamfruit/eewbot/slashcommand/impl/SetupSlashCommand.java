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
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.model.Channel;
import net.teamfruit.eewbot.registry.destination.model.ChannelSetting;
import net.teamfruit.eewbot.registry.destination.model.ChannelSettingType;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;
import net.teamfruit.eewbot.slashcommand.ISelectMenuSlashCommand;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import net.teamfruit.eewbot.slashcommand.SlashCommandUtils;
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
    private Mono<Boolean> validateWebhookUrl(SlashCommandContext ctx, ChannelWebhook webhook) {
        return Mono.fromCallable(() -> {
            // Use base URL without thread_id query parameter
            String baseUrl = ChannelWebhook.of(webhook.id(), webhook.token()).url();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = ctx.httpClient().send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            return status != 404;
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    Log.logger.warn("Failed to validate webhook URL, assuming valid", e);
                    return Mono.just(true);
                });
    }

    /**
     * Finds an existing bot-created webhook dedicated to this destination, or creates a new one.
     * Parent channels and threads intentionally do not share webhook IDs, even under the same
     * parent channel, so Discord webhook rate limits are distributed per destination.
     */
    private Mono<WebhookData> findOrCreateWebhook(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Snowflake guildId, GuildChannel guildChannel, long targetId) {
        boolean isThreadChannel = guildChannel instanceof ThreadChannel;
        long parentChannelId = isThreadChannel
                ? ((ThreadChannel) guildChannel).getParentId().map(Snowflake::asLong).orElseThrow()
                : targetId;

        return ctx.client().getRestClient().getWebhookService().getGuildWebhooks(guildId.asLong())
                .filter(webhook -> {
                    boolean isSameChannel = webhook.channelId().isPresent() && (webhook.channelId().get().asLong() == parentChannelId);
                    boolean isCreatedBySelf = webhook.user().toOptional()
                            .filter(user -> user.id().asLong() == event.getClient().getSelfId().asLong())
                            .isPresent();
                    boolean hasToken = webhook.token().isPresent();
                    // Webhooks are not shared across destinations. If a parent channel webhook is
                    // already assigned to one of its threads (or vice versa), create a fresh one.
                    return isSameChannel && isCreatedBySelf && hasToken && ctx.adminRegistry().isWebhookExclusiveToTarget(webhook.id().asLong(), targetId);
                })
                .next()
                .switchIfEmpty(ctx.client().getSelfMember(guildId).map(PartialMember::getDisplayName)
                        .flatMap(name -> event.getClient().getRestClient().getWebhookService()
                                .createWebhook(parentChannelId, WebhookCreateRequest.builder()
                                        .name(removeUnusableName(name, ctx.username()))
                                        .build(), "Create EEWBot webhook")));
    }

    /**
     * Registers a webhook for a target by converting WebhookData to ChannelWebhook and storing it.
     */
    private void registerWebhook(SlashCommandContext ctx, GuildChannel guildChannel, long targetId, WebhookData webhookData) {
        Long threadId = guildChannel instanceof ThreadChannel ? targetId : null;
        ChannelWebhook webhook = ChannelWebhook.of(
                webhookData.id().asLong(),
                webhookData.token().get(),
                threadId
        );
        ctx.adminRegistry().setWebhook(targetId, webhook);
    }

    /**
     * Checks MANAGE_WEBHOOKS permission; if present, finds/creates webhook and registers it.
     * If permission missing, shows reply with webhook warning.
     */
    private Mono<Message> createWebhookWithPermCheck(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Snowflake guildId, GuildChannel guildChannel, long targetId, PermissionSet perms, String lang) {
        if (!perms.contains(Permission.MANAGE_WEBHOOKS)) {
            return buildReply(ctx, event, lang, targetId, true);
        }
        return findOrCreateWebhook(ctx, event, guildId, guildChannel, targetId)
                .flatMap(webhookData -> {
                    registerWebhook(ctx, guildChannel, targetId, webhookData);
                    return buildReply(ctx, event, lang, targetId, false);
                });
    }

    @Override
    public Mono<Void> on(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, Channel channel, String lang) {
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
                                                    : event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.setup.permserror.sendmessages")).thenReturn(false))
                                            .flatMap(perms -> {
                                                if (channel == null) {
                                                    // Case 1: Not registered — register target and create webhook
                                                    SlashCommandUtils.createAndRegisterDefault(ctx.adminRegistry(), guildChannel, targetId, guildId, lang);
                                                    return createWebhookWithPermCheck(ctx, event, gid, guildChannel, targetId, perms, lang);
                                                } else if (channel.getWebhook() == null) {
                                                    // Case 2: Registered, no webhook — try to create webhook
                                                    return createWebhookWithPermCheck(ctx, event, gid, guildChannel, targetId, perms, lang);
                                                } else {
                                                    // Case 3: Registered, has webhook — validate with HEAD request
                                                    return validateWebhookUrl(ctx, channel.getWebhook())
                                                            .flatMap(valid -> {
                                                                if (valid) {
                                                                    // Webhook is valid, just show the menu
                                                                    return buildReply(ctx, event, lang, targetId, false);
                                                                } else {
                                                                    // Webhook returned 404, recreate it
                                                                    return createWebhookWithPermCheck(ctx, event, gid, guildChannel, targetId, perms, lang);
                                                                }
                                                            });
                                                }
                                            }))
                                    .onErrorResume(ClientException.isStatusCode(403), err -> event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.setup.permserror.viewchannel")))
                                    .thenReturn(true);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            // DM
                            if (channel == null) {
                                ctx.adminRegistry().put(targetId, Channel.createDefault(guildId, targetId, null, lang));
                            }
                            return buildReply(ctx, event, lang, targetId, false).thenReturn(true);
                        }))
                        .then(Mono.create(sink -> {
                            try {
                                ctx.adminRegistry().save();
                                sink.success();
                            } catch (IOException e) {
                                sink.error(e);
                            }
                        })));
    }

    private Mono<Message> buildReply(SlashCommandContext ctx, ApplicationCommandInteractionEvent event, String lang, long channelId, boolean noWebhook) {
        return event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.setup.reply") + (noWebhook ? "\n\n" + ctx.i18n().get(lang, "eewbot.scmd.setup.permserror.managewebhooks") : ""))
                .withComponents(ActionRow.of(buildSelectMenu(ctx, ChannelSettingType.BASE, channelId, lang)),
                        ActionRow.of(buildSelectMenu(ctx, ChannelSettingType.MODIFIER, channelId, lang)),
                        ActionRow.of(buildSensitivitySelectMenu(ctx, channelId, lang)));
    }

    private SelectMenu buildSelectMenu(SlashCommandContext ctx, ChannelSettingType type, long channelId, String lang) {
        Map<String, Boolean> fields = ctx.adminRegistry().get(channelId).getSettingsByType(type);
        return SelectMenu.of(type.getCustomId(), fields.entrySet().stream().map(entry -> {
                    String label = ctx.i18n().get(lang, "eewbot.scmd.setup." + type.getCustomId() + "." + entry.getKey().toLowerCase() + ".label");
                    SelectMenu.Option option = entry.getValue() ? SelectMenu.Option.ofDefault(label, entry.getKey()) : SelectMenu.Option.of(label, entry.getKey());
                    option = option.withDescription(ctx.i18n().get(lang, "eewbot.scmd.setup." + type.getCustomId() + "." + entry.getKey().toLowerCase() + ".desc"));
                    return option;
                }).collect(Collectors.toList()))
                .withPlaceholder(ctx.i18n().get(lang, "eewbot.scmd.setup." + type.getCustomId() + ".placeholder"))
                .withMinValues(0)
                .withMaxValues(fields.size());
    }

    private SelectMenu buildSensitivitySelectMenu(SlashCommandContext ctx, long channelId, String lang) {
        Channel channel = ctx.adminRegistry().get(channelId);
        return SelectMenu.of("sensitivity", Arrays.stream(SeismicIntensity.values()).map(intensity -> {
                    String label = intensity == SeismicIntensity.UNKNOWN ? ctx.i18n().get(lang, "eewbot.scmd.setup.sensitivity.option.unknown") : ctx.i18n().format(lang, "eewbot.scmd.setup.sensitivity.option", intensity);
                    if (channel.getMinIntensity() == intensity)
                        return SelectMenu.Option.ofDefault(label, intensity.getSimple());
                    return SelectMenu.Option.of(label, intensity.getSimple());
                }).collect(Collectors.toList()))
                .withPlaceholder(ctx.i18n().get(lang, "eewbot.scmd.setup.sensitivity.placeholder"))
                .withMinValues(1)
                .withMaxValues(1);
    }

    @Override
    public Mono<Void> onSelect(SlashCommandContext ctx, SelectMenuInteractionEvent event, String lang) {
        if (ChannelSettingType.hasCustomId(event.getCustomId()))
            return applyChannel(ctx, event, lang).then();
        else if (event.getCustomId().equals("sensitivity"))
            return applySensitivity(ctx, event, lang).then();
        return Mono.empty();
    }

    private Mono<Message> applyChannel(SlashCommandContext ctx, SelectMenuInteractionEvent event, String lang) {
        long targetId = event.getInteraction().getChannelId().asLong();
        Map<String, Boolean> settings = Arrays.stream(Channel.class.getDeclaredFields())
                .filter(field -> {
                    if (!field.isAnnotationPresent(ChannelSetting.class))
                        return false;
                    ChannelSetting annotation = field.getAnnotation(ChannelSetting.class);
                    return annotation != null && annotation.value().getCustomId().equals(event.getCustomId());
                })
                .map(Field::getName)
                .collect(Collectors.toMap(name -> name, name -> event.getValues().contains(name)));
        ctx.adminRegistry().setAll(targetId, settings);
        try {
            ctx.adminRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        if (event.getValues().isEmpty())
            return event.createFollowup(ctx.i18n().get(lang, "eewbot.scmd.setup." + event.getCustomId() + ".followup.none"));
        return event.createFollowup(ctx.i18n().format(lang, "eewbot.scmd.setup." + event.getCustomId() + ".followup.any",
                event.getValues().stream()
                        .map(value -> Channel.toI18nKey(value)
                                .map(key -> ctx.i18n().format(lang, key))
                                .orElse(""))
                        .collect(Collectors.joining(", "))));
    }

    private Mono<Message> applySensitivity(SlashCommandContext ctx, SelectMenuInteractionEvent event, String lang) {
        long targetId = event.getInteraction().getChannelId().asLong();
        SeismicIntensity intensity = SeismicIntensity.get(event.getValues().getFirst());
        ctx.adminRegistry().setMinIntensity(targetId, intensity);
        try {
            ctx.adminRegistry().save();
        } catch (IOException e) {
            return Mono.error(e);
        }
        return event.createFollowup(ctx.i18n().format(lang, intensity != SeismicIntensity.UNKNOWN ? "eewbot.scmd.setup.sensitivity.followup" : "eewbot.scmd.setup.sensitivity.followup.unknown", intensity.getSimple()));
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
