package net.teamfruit.eewbot.quakeinfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.discord.EmbedPacker;
import net.teamfruit.eewbot.entity.discord.EmbedRenderer;
import net.teamfruit.eewbot.entity.discord.I18nEmbedBuilder;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Immutable exact wire bodies; a retry must reuse both the ID and these bytes. */
public record QuakeInfoSnapshot(String snapshotId, String metadataJson, String contentHash,
                                String sourceKey, Map<String, List<String>> messages) {
    public static final List<String> LANGUAGES = List.of("ja_jp", "en_us", "zh_tw", "ko_hanja", "ko_kp");
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;

    public QuakeInfoSnapshot {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        messages.forEach((lang, bodies) -> copy.put(lang, List.copyOf(bodies)));
        messages = Collections.unmodifiableMap(copy);
    }

    public static QuakeInfoSnapshot render(QuakeInfo quake, EmbedContext context, long epoch, long sequence) {
        JMAReport report = (JMAReport) quake;
        JsonObject metadata = new JsonObject();
        String id = UUID.randomUUID().toString();
        metadata.addProperty("snapshotId", id);
        metadata.addProperty("schemaVersion", 1);
        metadata.addProperty("eventId", quake.getEventId());
        metadata.addProperty("reportType", JMAXmlType.from(report.getClass()).name());
        addInstant(metadata, "reportDateTime", report.getReportDateTime());
        addInstant(metadata, "dateTime", report.getDateTime());
        int serial;
        try { serial = Integer.parseInt(report.getSerial()); }
        catch (RuntimeException e) { serial = -1; }
        metadata.addProperty("serial", serial);
        metadata.addProperty("infoType", report.getInfoType().name());
        metadata.addProperty("infoTypePriority", report.isCancelReport() ? 2 : report.getInfoType() == JMAInfoType.訂正 ? 1 : 0);
        metadata.addProperty("isCancel", report.isCancelReport());
        TreeMap<String, Object> source = new TreeMap<>();
        metadata.entrySet().stream().filter(e -> !e.getKey().equals("snapshotId")).forEach(e -> source.put(e.getKey(), e.getValue()));
        String sourceKey = sha256(Codecs.GSON.toJson(source));
        metadata.addProperty("renderedAt", Instant.now().toString());
        metadata.addProperty("publisherEpoch", epoch);
        metadata.addProperty("publisherSequence", sequence);
        JsonObject manifest = new JsonObject();
        Map<String, List<String>> messages = new LinkedHashMap<>();
        StringBuilder digestInput = new StringBuilder();
        int totalBytes = 0;
        for (String lang : LANGUAGES) {
            // I18n normalizes ja_JP/en_US/zh_TW/ko_Hanja/ko_KP to lowercase.
            var packed = EmbedPacker.pack(quake.createEmbeds(lang, context, () -> new I18nEmbedBuilder(lang, context.i18n())));
            if (packed.isEmpty() || packed.size() > 16)
                throw new IllegalArgumentException("QuakeInfo message count exceeds snapshot contract");
            List<String> bodies = new ArrayList<>();
            JsonArray expected = new JsonArray();
            int languageBytes = 0;
            for (var message : packed) {
                String body = Codecs.GSON.toJson(EmbedRenderer.toDiscordEmbeds(message));
                int size = body.getBytes(StandardCharsets.UTF_8).length;
                if (size > MAX_MESSAGE_BYTES)
                    throw new IllegalArgumentException("QuakeInfo message exceeds snapshot byte limit");
                String hash = sha256(body);
                JsonObject entry = new JsonObject();
                entry.addProperty("contentHash", hash);
                entry.addProperty("byteLength", size);
                expected.add(entry);
                digestInput.append(lang).append(':').append(bodies.size()).append(':').append(hash).append(':').append(size).append('\n');
                bodies.add(body);
                languageBytes += size;
            }
            if (languageBytes > 512 * 1024)
                throw new IllegalArgumentException("QuakeInfo language exceeds snapshot byte limit");
            totalBytes += languageBytes;
            messages.put(lang, bodies);
            manifest.add(lang, expected);
        }
        if (totalBytes > 2 * 1024 * 1024)
            throw new IllegalArgumentException("QuakeInfo snapshot exceeds byte limit");
        String hash = sha256(digestInput.toString());
        metadata.addProperty("contentHash", hash);
        metadata.add("languages", manifest);
        return new QuakeInfoSnapshot(id, metadata.toString(), hash, sourceKey, messages);
    }

    private static void addInstant(JsonObject object, String name, Instant value) {
        if (value == null) object.add(name, JsonNull.INSTANCE);
        else object.addProperty(name, value.toString());
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
