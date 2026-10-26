package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.databind.JsonNode;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, all-or-nothing v1 conversion; never used by delivery lookups. */
public final class RemoteDestinationConfigSource implements DestinationConfigSource {
    private static final long MAX_REVISION = 9007199254740991L;
    private static final Pattern WEBHOOK = Pattern.compile(
            "https://discord\\.com/api/webhooks/([1-9][0-9]{0,18})/[A-Za-z0-9_-]+(?:\\?[^#\\s]*)?");
    private final RemoteConfigApiClient client;
    private long lastSnapshotRevision = -1;
    private long lastPolledRevision = -1;
    private boolean rollback;

    public RemoteDestinationConfigSource(RemoteConfigApiClient client) {
        this.client = client;
    }

    @Override
    public synchronized long getRevision() {
        checkRollback();
        long revision = revision(this.client.revision());
        detectRollback(revision, Math.max(this.lastSnapshotRevision, this.lastPolledRevision));
        this.lastPolledRevision = revision;
        return revision;
    }

    @Override
    public synchronized DeliverySnapshot loadSnapshot() {
        checkRollback();
        JsonNode json = this.client.snapshot();
        long revision = revision(json);
        detectRollback(revision, this.lastSnapshotRevision);
        List<DeliverySnapshot.DeliveryChannel> channels = new ArrayList<>();
        Set<Long> targets = new HashSet<>();
        try {
            JsonNode destinations = required(json, "destinations");
            if (!destinations.isArray()) throw invalid();
            for (JsonNode destination : destinations) {
                long target = snowflake(required(destination, "targetId"), false);
                long channel = snowflake(required(destination, "channelId"), false);
                Long thread = snowflake(required(destination, "threadId"), true);
                Long guild = snowflake(required(destination, "guildId"), true);
                if (!targets.add(target)) throw invalid();
                int intensity = integer(required(destination, "minIntensity"), 9).intValue();
                String lang = string(required(destination, "lang"));
                JsonNode webhookUrl = required(destination, "webhookUrl");
                Long webhookId = snowflake(required(destination, "webhookId"), true);
                ChannelWebhook webhook = webhook(webhookUrl, webhookId);
                channels.add(new DeliverySnapshot.DeliveryChannel(target, channel, thread, guild,
                        flag(destination, "eewAlert"), flag(destination, "eewPrediction"),
                        flag(destination, "eewDecimation"), flag(destination, "quakeInfo"), flag(destination, "tsunami"),
                        SeismicIntensity.fromCode(intensity), lang, webhook));
            }
        } catch (RuntimeException e) {
            // Never retain parser/URL/numeric exceptions that can contain confidential input.
            throw invalid();
        }
        DeliverySnapshot snapshot = new DeliverySnapshot(revision, channels);
        this.lastSnapshotRevision = revision;
        return snapshot;
    }

    private void detectRollback(long revision, long previous) {
        if (revision < previous) {
            this.rollback = true;
            checkRollback();
        }
    }

    private void checkRollback() {
        if (this.rollback) throw new RemoteConfigException(
                "Remote revision rollback detected; retaining last snapshot. Operator restart required to accept restored D1 state");
    }

    private static long revision(JsonNode object) {
        return integer(required(object, "revision"), MAX_REVISION);
    }

    private static Long integer(JsonNode value, long max) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0 || value.longValue() > max) {
            throw invalid();
        }
        return value.longValue();
    }

    private static JsonNode required(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null) throw invalid();
        return value;
    }

    private static String string(JsonNode value) {
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static Long snowflake(JsonNode value, boolean nullable) {
        if (nullable && value.isNull()) return null;
        String text = string(value);
        if (!text.matches("[1-9][0-9]{0,18}")) throw invalid();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // ChannelWebhook.id() and the existing SQL paths use signed long IDs.
            throw new RemoteConfigException("Remote Snowflake exceeds supported positive signed 64-bit range");
        }
    }

    private static boolean flag(JsonNode object, String name) {
        JsonNode value = required(object, name);
        if (!value.isBoolean()) throw invalid();
        return value.booleanValue();
    }

    private static ChannelWebhook webhook(JsonNode value, Long id) {
        if (value.isNull()) {
            if (id != null) throw invalid();
            return null;
        }
        String url = string(value);
        Matcher matcher = WEBHOOK.matcher(url);
        if (id == null || !matcher.matches() || Long.parseLong(matcher.group(1)) != id) throw invalid();
        // Validate query Snowflakes without rebuilding or discarding the stored query.
        String query = URI.create(url).getRawQuery();
        int threads = 0;
        if (query != null) {
            for (String parameter : query.split("&")) {
                String[] pair = parameter.split("=", 2);
                if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals("thread_id")) {
                    if (++threads > 1 || pair.length != 2) throw invalid();
                    String thread = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                    if (!thread.matches("[1-9][0-9]{0,18}") || Long.parseLong(thread) <= 0) throw invalid();
                }
            }
        }
        return new ChannelWebhook(url);
    }

    private static RemoteConfigException invalid() {
        return new RemoteConfigException("Invalid remote snapshot or revision; complete update rejected (check fields, types, Snowflake range and webhook consistency)");
    }
}
