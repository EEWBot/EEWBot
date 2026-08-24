package net.teamfruit.eewbot.entity.discord;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Estimates the empirically observed effective cost of Components V2 on Execute Webhook.
 * This is not a raw request-size or a documented Discord limit.
 */
public final class WebhookEffectiveCostEstimator {

    public static final long OBSERVED_EFFECTIVE_BUDGET = 10170;

    private static final long TOP_LEVEL_TEXT_COST = 12;
    private static final long TOP_LEVEL_SEPARATOR_COST = 11;
    private static final long CONTAINER_WRAPPER_COST = 13;
    private static final long CONTAINER_TEXT_COST = 8;
    private static final long CONTAINER_SEPARATOR_COST = 10;
    private static final long CONTAINER_ACCENT_COST = 6;
    private static final long CONTAINER_SPOILER_COST = 2;
    private static final Pattern CHARACTERIZED_MEDIA_URL = Pattern.compile(
            "https://embed-bug-tester\\.yr32\\.net/\\d{10}");

    private WebhookEffectiveCostEstimator() {
    }

    public static Result estimate(final List<PendingComponent> components) {
        long minimumKnownCost = 0;
        boolean indeterminate = false;
        for (final PendingComponent component : components) {
            final Cost cost = cost(component, false);
            minimumKnownCost += cost.minimumKnownCost();
            indeterminate |= cost.indeterminate();
        }
        if (minimumKnownCost > OBSERVED_EFFECTIVE_BUDGET)
            return new TooLarge(minimumKnownCost);
        if (indeterminate)
            return new Indeterminate(minimumKnownCost);
        return new Safe(minimumKnownCost);
    }

    private static Cost cost(final PendingComponent component, final boolean insideContainer) {
        if (component instanceof PendingComponent.Text text)
            return Cost.known(ComponentLimits.utf8Bytes(text.content())
                    + (insideContainer ? CONTAINER_TEXT_COST : TOP_LEVEL_TEXT_COST));
        if (component instanceof PendingComponent.Separator separator) {
            if (!separator.divider() || separator.spacing() != PendingComponent.Spacing.SMALL)
                return Cost.unknown();
            return Cost.known(insideContainer ? CONTAINER_SEPARATOR_COST : TOP_LEVEL_SEPARATOR_COST);
        }
        if (component instanceof PendingComponent.Container container) {
            long value = CONTAINER_WRAPPER_COST;
            boolean indeterminate = false;
            for (final PendingComponent child : container.children()) {
                final Cost childCost = cost(child, true);
                value += childCost.minimumKnownCost();
                indeterminate |= childCost.indeterminate();
            }
            if (container.accentColor() != null)
                value += CONTAINER_ACCENT_COST;
            if (container.spoiler())
                value += CONTAINER_SPOILER_COST;
            return new Cost(value, indeterminate);
        }
        if (component instanceof PendingComponent.Section section) {
            final long minimumTextCost = section.children().stream()
                    .mapToLong(text -> ComponentLimits.utf8Bytes(text.content())).sum();
            return new Cost(minimumTextCost, true);
        }
        if (!insideContainer && component instanceof PendingComponent.MediaGallery gallery
                && isCharacterized(gallery)) {
            final int items = gallery.items().size();
            return Cost.known(items == 1 ? 172 : 163L * items + 9);
        }
        // General Media Gallery, Section/Thumbnail, and their container-child costs are not characterized.
        return Cost.unknown();
    }

    private static boolean isCharacterized(final PendingComponent.MediaGallery gallery) {
        return !gallery.items().isEmpty()
                && gallery.items().size() <= ComponentLimits.MAX_MEDIA_GALLERY_ITEMS
                && gallery.items().stream().allMatch(item ->
                item.url() != null && CHARACTERIZED_MEDIA_URL.matcher(item.url()).matches()
                        && item.description() == null && !item.spoiler());
    }

    public sealed interface Result permits Safe, TooLarge, Indeterminate {
    }

    public record Safe(long effectiveCost) implements Result {
    }

    public record TooLarge(long minimumEffectiveCost) implements Result {
    }

    public record Indeterminate(long minimumKnownEffectiveCost) implements Result {
    }

    private record Cost(long minimumKnownCost, boolean indeterminate) {
        private static Cost known(final long value) {
            return new Cost(value, false);
        }

        private static Cost unknown() {
            return new Cost(0, true);
        }
    }
}
