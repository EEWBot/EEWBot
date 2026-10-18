package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Applies character and measured webhook limits, preserving description and field text. */
public final class EmbedPacker {

    private EmbedPacker() {
    }

    public static List<List<PendingEmbed>> pack(final List<PendingEmbed> embeds) {
        final List<PendingEmbed> pages = new ArrayList<>();
        for (final PendingEmbed embed : embeds)
            pages.addAll(paginate(normalize(embed)));
        if (pages.isEmpty())
            pages.add(PendingEmbed.empty());
        return group(pages);
    }

    static PendingEmbed normalize(final PendingEmbed embed) {
        final List<PendingField> fields = new ArrayList<>();
        for (final PendingField field : embed.fields()) {
            final String name = truncateWarn(field.name(), DiscordLimits.MAX_FIELD_NAME, "field name");
            if (DiscordLimits.codePoints(field.value()) <= DiscordLimits.MAX_FIELD_VALUE) {
                fields.add(new PendingField(name, field.value(), field.inline()));
                continue;
            }
            boolean first = true;
            for (final String chunk : splitValue(field.value())) {
                fields.add(new PendingField(first ? name : "", chunk, field.inline()));
                first = false;
            }
        }
        return new PendingEmbed(
                truncateWarn(embed.title(), DiscordLimits.MAX_TITLE, "title"), embed.description(),
                sanitizeUrl(embed.url(), "url"), embed.timestamp(), embed.color(),
                truncateWarn(embed.footerText(), DiscordLimits.MAX_FOOTER_TEXT, "footer text"),
                sanitizeUrl(embed.footerIcon(), "footer icon"), sanitizeUrl(embed.image(), "image"),
                sanitizeUrl(embed.thumbnail(), "thumbnail"),
                truncateWarn(embed.authorName(), DiscordLimits.MAX_AUTHOR_NAME, "author name"),
                sanitizeUrl(embed.authorUrl(), "author url"), sanitizeUrl(embed.authorIcon(), "author icon"), fields);
    }

    static List<String> splitValue(final String value) {
        return splitText(value, text -> DiscordLimits.codePoints(text) <= DiscordLimits.MAX_FIELD_VALUE);
    }

    /** Keeps every character, including the newline used as a split boundary. */
    static List<String> splitText(final String value, final Predicate<String> fits) {
        if (value == null || value.isEmpty())
            return List.of();
        final List<String> parts = new ArrayList<>();
        String remaining = value;
        while (!fits.test(remaining)) {
            int low = 0;
            int high = DiscordLimits.codePoints(remaining);
            while (low < high) {
                final int middle = low + (high - low + 1) / 2;
                if (fits.test(remaining.substring(0, remaining.offsetByCodePoints(0, middle))))
                    low = middle;
                else
                    high = middle - 1;
            }
            if (low == 0)
                throw new IllegalArgumentException("No text fits in the embed's remaining budget");
            int cut = remaining.offsetByCodePoints(0, low);
            final int newline = remaining.lastIndexOf('\n', cut - 1);
            if (newline > 0)
                cut = newline + 1;
            parts.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut);
        }
        if (!remaining.isEmpty())
            parts.add(remaining);
        return parts;
    }

    private static String truncateWarn(final String text, final int limit, final String what) {
        final String fitted = DiscordLimits.truncate(text, limit);
        if (!Objects.equals(text, fitted))
            Log.logger.warn("Truncating embed {} from {} code points to {}: Discord's limit for this property",
                    what, DiscordLimits.codePoints(text), limit);
        return fitted;
    }

    private static String sanitizeUrl(final String url, final String what) {
        if (url == null || DiscordLimits.codePoints(url) <= DiscordLimits.MAX_URL)
            return url;
        Log.logger.warn("Dropping embed {} URL: {} code points exceeds the limit of {}",
                what, DiscordLimits.codePoints(url), DiscordLimits.MAX_URL);
        return null;
    }

    static List<PendingEmbed> paginate(final PendingEmbed source) {
        final PendingEmbed plain = source.colorChromeOnly().withFields(List.of());
        final List<PendingEmbed> pages = new ArrayList<>();
        PendingEmbed current = fitUrls(source.headChromeOnly().withDescription(null).withFields(List.of()));

        String remaining = source.description();
        while (remaining != null && !remaining.isEmpty()) {
            final PendingEmbed chrome = current;
            final Predicate<String> fits = text -> DiscordLimits.codePoints(text) <= DiscordLimits.MAX_DESCRIPTION
                    && fitsPage(chrome.withDescription(text));
            final String first = remaining.substring(0, remaining.offsetByCodePoints(0, 1));
            if (!fits.test(first)) {
                pages.add(current);
                current = plain;
                continue;
            }
            final String part = splitText(remaining, fits).getFirst();
            current = current.withDescription(part);
            remaining = remaining.substring(part.length());
            if (!remaining.isEmpty()) {
                pages.add(current);
                current = plain;
            }
        }

        for (final PendingField field : source.fields()) {
            final List<PendingField> candidate = new ArrayList<>(current.fields());
            candidate.add(field);
            if (fitsPage(current.withFields(candidate))) {
                current = current.withFields(List.copyOf(candidate));
            } else {
                pages.add(current);
                current = plain.withFields(List.of(field));
                if (!fitsPage(current))
                    throw new IllegalArgumentException("A normalized field exceeds a whole embed page");
            }
        }

        final PendingEmbed tail = fitUrls(source.tailChromeOnly().withFields(List.of()));
        final PendingEmbed combined = withTail(current, tail);
        if (fitsPage(combined)) {
            pages.add(combined);
        } else {
            pages.add(current);
            pages.add(tail);
        }
        return pages;
    }

    private static PendingEmbed withTail(final PendingEmbed page, final PendingEmbed tail) {
        return new PendingEmbed(page.title(), page.description(), page.url(), tail.timestamp(), page.color(),
                tail.footerText(), tail.footerIcon(), tail.image(), page.thumbnail(),
                tail.authorName(), tail.authorUrl(), tail.authorIcon(), page.fields());
    }

    /** Retains the existing URL removal priority for unusually large decorations. Never trims body text. */
    private static PendingEmbed fitUrls(final PendingEmbed page) {
        final String[] urls = {page.authorIcon(), page.footerIcon(), page.thumbnail(),
                page.authorUrl(), page.url(), page.image()};
        PendingEmbed fitted = page;
        for (int i = 0; i < urls.length && !fitsPage(fitted); i++) {
            if (urls[i] == null)
                continue;
            Log.logger.warn("Dropping an embed decoration URL: its chrome exceeds the webhook effective budget");
            urls[i] = null;
            fitted = new PendingEmbed(page.title(), page.description(), urls[4], page.timestamp(), page.color(),
                    page.footerText(), urls[1], urls[5], urls[2], page.authorName(), urls[3], urls[0], page.fields());
        }
        if (!fitsPage(fitted))
            throw new IllegalArgumentException("Embed chrome exceeds a whole message");
        return fitted;
    }

    private static boolean fitsPage(final PendingEmbed page) {
        return page.fields().size() <= DiscordLimits.MAX_FIELDS_PER_EMBED
                && page.charCount() <= DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                && EmbedWebhookCost.fits(List.of(page));
    }

    static List<List<PendingEmbed>> group(final List<PendingEmbed> pages) {
        final List<List<PendingEmbed>> messages = new ArrayList<>();
        List<PendingEmbed> current = new ArrayList<>();
        int chars = 0;
        for (final PendingEmbed page : pages) {
            final List<PendingEmbed> candidate = new ArrayList<>(current);
            candidate.add(page);
            if (!current.isEmpty() && (candidate.size() > DiscordLimits.MAX_EMBEDS_PER_MESSAGE
                    || chars + page.charCount() > DiscordLimits.MAX_TOTAL_CHARS_PER_MESSAGE
                    || !EmbedWebhookCost.fits(candidate))) {
                messages.add(List.copyOf(current));
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(page);
            chars += page.charCount();
        }
        messages.add(List.copyOf(current));
        return messages;
    }
}
