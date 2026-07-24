package net.teamfruit.eewbot;

import net.teamfruit.eewbot.registry.destination.model.ChannelWebhook;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks secrets (webhook tokens, credentials) in log output while preserving
 * identifiers useful for investigation (webhook id, host, path, status code, exception type).
 */
public class LogSanitizer {

    private static final Pattern DISCORD_WEBHOOK_URL_PATTERN = Pattern.compile(
            "https://(?:discord\\.com|discordapp\\.com|ptb\\.discord\\.com|canary\\.discord\\.com)/api/webhooks/(\\d+)/[^\\s\"'?]+"
    );

    private LogSanitizer() {
    }

    /**
     * Mask a single URL for safe logging.
     * Discord webhook URLs are delegated to {@link ChannelWebhook#maskWebhookUrl(String)}.
     * Other URLs keep scheme/host/port/path, masking only userinfo and query values.
     * Strings that are not URLs, or null, are returned unchanged.
     */
    public static String maskUrl(String url) {
        if (url == null) return null;
        if (DISCORD_WEBHOOK_URL_PATTERN.matcher(url).lookingAt()) {
            return ChannelWebhook.maskWebhookUrl(url);
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return url;
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            return url;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(uri.getScheme()).append("://");
        if (uri.getRawUserInfo() != null) {
            sb.append("***@");
        }
        sb.append(uri.getHost());
        if (uri.getPort() != -1) {
            sb.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            sb.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            sb.append('?').append(maskQueryValues(uri.getRawQuery()));
        }
        if (uri.getRawFragment() != null) {
            sb.append('#').append(uri.getRawFragment());
        }
        return sb.toString();
    }

    private static String maskQueryValues(String query) {
        StringBuilder sb = new StringBuilder();
        String[] params = query.split("&");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append('&');
            String param = params[i];
            int eq = param.indexOf('=');
            if (eq < 0) {
                sb.append(param);
            } else {
                sb.append(param, 0, eq).append("=***");
            }
        }
        return sb.toString();
    }

    /**
     * Replace the token portion of any Discord webhook URLs found in free-form text with "***".
     * Text outside matched URLs is left unchanged. Null-safe.
     */
    public static String maskDiscordWebhookUrlsInText(String text) {
        if (text == null) return null;
        Matcher matcher = DISCORD_WEBHOOK_URL_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(buildMaskedUrl(matcher.group(0), matcher.group(1))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String buildMaskedUrl(String fullMatch, String webhookId) {
        int schemeEnd = fullMatch.indexOf("://") + 3;
        int hostEnd = fullMatch.indexOf('/', schemeEnd);
        String hostPart = fullMatch.substring(0, hostEnd);
        return hostPart + "/api/webhooks/" + webhookId + "/***";
    }

    /**
     * Return a log-safe exception message: the original message with any Discord webhook URLs masked,
     * or the exception class name if the message is null.
     */
    public static String safeExceptionMessage(Throwable t) {
        if (t == null) return null;
        String message = t.getMessage();
        if (message == null) return t.getClass().getName();
        return maskDiscordWebhookUrlsInText(message);
    }
}
