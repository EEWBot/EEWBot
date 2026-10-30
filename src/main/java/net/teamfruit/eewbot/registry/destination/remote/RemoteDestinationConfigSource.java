package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.databind.JsonNode;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.destination.DestinationConfigSource;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, all-or-nothing v2 conversion; never used by delivery lookups. */
public final class RemoteDestinationConfigSource implements DestinationConfigSource {
    private static final long MAX_REVISION = 9007199254740991L;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,256}");
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
                ChannelWebhook webhook = webhook(required(destination, "webhook"), thread);
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

    private static ChannelWebhook webhook(JsonNode value, Long thread) {
        if (value.isNull()) return null;
        if (!value.isObject()) throw invalid();
        long id = snowflake(required(value, "id"), false);
        String token = string(required(value, "token"));
        if (!TOKEN.matcher(token).matches()) throw invalid();
        // v2 sends id/token only; Core owns the host and thread query so the remote cannot redirect delivery.
        String url = "https://discord.com/api/webhooks/" + id + "/" + token;
        return new ChannelWebhook(thread == null ? url : url + "?thread_id=" + thread);
    }

    private static RemoteConfigException invalid() {
        return new RemoteConfigException("Invalid remote snapshot or revision; complete update rejected (check fields, types, Snowflake range and webhook consistency)");
    }
}
