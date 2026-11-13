package net.teamfruit.eewbot.quakeinfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.discord.DiscordLimits;
import net.teamfruit.eewbot.entity.discord.EmbedPacker;
import net.teamfruit.eewbot.entity.discord.EmbedRenderer;
import net.teamfruit.eewbot.entity.discord.I18nEmbedBuilder;
import net.teamfruit.eewbot.entity.jma.JMAReport;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** Exact body of PUT /quakeinfo/latest; a retry resends these bytes unchanged. */
public record QuakeInfoSnapshot(String body, String fingerprint) {
    public static final List<String> LANGUAGES = List.of("ja_jp", "en_us", "zh_tw", "ko_hanja", "ko_kp");
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;

    public static QuakeInfoSnapshot render(QuakeInfo quake, EmbedContext context) {
        JMAReport report = (JMAReport) quake;
        JsonObject root = new JsonObject();
        root.addProperty("eventId", quake.getEventId());
        root.addProperty("reportType", JMAXmlType.from(report.getClass()).name());
        addInstant(root, "reportDateTime", report.getReportDateTime());
        addInstant(root, "dateTime", report.getDateTime());
        int serial;
        try { serial = Integer.parseInt(report.getSerial()); }
        catch (RuntimeException e) { serial = -1; }
        root.addProperty("serial", serial);
        // The Worker derives the ordering priority from infoType (発表 < 訂正 < 取消).
        root.addProperty("infoType", report.getInfoType().name());
        JsonObject languages = new JsonObject();
        int totalBytes = 0;
        for (String lang : LANGUAGES) {
            // I18n normalizes ja_JP/en_US/zh_TW/ko_Hanja/ko_KP to lowercase.
            var packed = EmbedPacker.pack(quake.createEmbeds(lang, context, () -> new I18nEmbedBuilder(lang, context.i18n())));
            if (packed.isEmpty() || packed.size() > 16)
                throw new IllegalArgumentException("QuakeInfo message count exceeds snapshot contract");
            JsonArray messages = new JsonArray();
            int languageBytes = 0;
            for (var message : packed) {
                var embeds = Codecs.GSON.toJsonTree(EmbedRenderer.toDiscordEmbeds(message)).getAsJsonArray();
                if (embeds.isEmpty() || embeds.size() > DiscordLimits.MAX_EMBEDS_PER_MESSAGE)
                    throw new IllegalArgumentException("QuakeInfo message embed count exceeds snapshot contract");
                int size = embeds.toString().getBytes(StandardCharsets.UTF_8).length;
                if (size > MAX_MESSAGE_BYTES)
                    throw new IllegalArgumentException("QuakeInfo message exceeds snapshot byte limit");
                messages.add(embeds);
                languageBytes += size;
            }
            if (languageBytes > 512 * 1024)
                throw new IllegalArgumentException("QuakeInfo language exceeds snapshot byte limit");
            totalBytes += languageBytes;
            languages.add(lang, messages);
        }
        if (totalBytes > 2 * 1024 * 1024)
            throw new IllegalArgumentException("QuakeInfo snapshot exceeds byte limit");
        root.add("languages", languages);
        String body = root.toString();
        return new QuakeInfoSnapshot(body, sha256(body));
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
