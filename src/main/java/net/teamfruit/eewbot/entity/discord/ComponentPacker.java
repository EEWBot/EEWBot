package net.teamfruit.eewbot.entity.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Applies Discord Components V2 limits without silently dropping text.
 */
public final class ComponentPacker {

    private ComponentPacker() {
    }

    public static List<List<PendingComponent>> pack(final List<PendingComponent> components) {
        final List<List<PendingComponent>> messages = new ArrayList<>();
        for (final PendingComponent component : components) {
            if (component instanceof PendingComponent.Container container) {
                for (final PendingComponent.Container page : paginate(container))
                    append(messages, page);
            } else if (component instanceof PendingComponent.TextDisplay text) {
                for (final String part : splitText(text.content(), value ->
                        fit(List.of(new PendingComponent.TextDisplay(value))) == Fit.SAFE))
                    append(messages, new PendingComponent.TextDisplay(part));
            } else if (component instanceof PendingComponent.Section section) {
                for (final PendingComponent normalized : normalizeSection(section, null, null))
                    append(messages, normalized);
            } else if (component instanceof PendingComponent.MediaGallery gallery) {
                for (final PendingComponent normalized : normalizeChildren(List.of(gallery), null))
                    append(messages, normalized);
            } else {
                append(messages, component);
            }
        }
        return messages;
    }

    private static void append(final List<List<PendingComponent>> messages, final PendingComponent component) {
        if (!messages.isEmpty()) {
            final List<PendingComponent> current = messages.getLast();
            if (fit(current) == Fit.SAFE) {
                final List<PendingComponent> candidate = new ArrayList<>(current);
                candidate.add(component);
                if (fit(candidate) == Fit.SAFE) {
                    messages.set(messages.size() - 1, List.copyOf(candidate));
                    return;
                }
            }
        }
        if (fit(List.of(component)) == Fit.DOES_NOT_FIT)
            throw new IllegalArgumentException("A component cannot be represented within Discord's message limits: " + component);
        messages.add(List.of(component));
    }

    private static List<PendingComponent.Container> paginate(final PendingComponent.Container source) {
        final List<PendingComponent> normalized = normalizeChildren(source.components(), source);
        final List<PendingComponent.Container> pages = new ArrayList<>();
        List<PendingComponent> current = new ArrayList<>();
        for (final PendingComponent child : normalized) {
            final List<PendingComponent> candidate = new ArrayList<>(current);
            candidate.add(child);
            final Fit candidateFit = fit(List.of(source.withComponents(candidate)));
            if (candidateFit == Fit.SAFE) {
                current = candidate;
                continue;
            }
            final Fit childFit = fit(List.of(source.withComponents(List.of(child))));
            if (childFit == Fit.INDETERMINATE) {
                final List<PendingComponent> isolated = new ArrayList<>();
                PendingComponent.Separator prefix = null;
                if (!current.isEmpty() && current.getLast() instanceof PendingComponent.Separator separator) {
                    prefix = separator;
                    current.removeLast();
                    isolated.add(separator);
                }
                isolated.add(child);

                Fit isolatedFit = fit(List.of(source.withComponents(isolated)));
                if (isolatedFit == Fit.DOES_NOT_FIT && prefix != null) {
                    current.add(prefix);
                    isolated.removeFirst();
                    isolatedFit = fit(List.of(source.withComponents(isolated)));
                }
                addPage(pages, source, current);
                current = new ArrayList<>();
                if (isolatedFit == Fit.DOES_NOT_FIT)
                    throw new IllegalArgumentException("A container child cannot be represented within Discord's limits: " + child);
                pages.add(source.withComponents(isolated));
                continue;
            }

            addPage(pages, source, current);
            current = new ArrayList<>();
            if (childFit == Fit.SAFE) {
                current.add(child);
                continue;
            }
            if (childFit == Fit.INDETERMINATE) {
                pages.add(source.withComponents(List.of(child)));
                continue;
            }
            if (child instanceof PendingComponent.TextDisplay text) {
                final Predicate<String> predicate = value -> fit(List.of(source.withComponents(
                        List.of(new PendingComponent.TextDisplay(value))))) == Fit.SAFE;
                for (final String part : splitText(text.content(), predicate)) {
                    addPage(pages, source, current);
                    current = new ArrayList<>();
                    current.add(new PendingComponent.TextDisplay(part));
                }
                continue;
            }
            throw new IllegalArgumentException("A container child cannot be represented within Discord's limits: " + child);
        }
        addPage(pages, source, current);
        return pages;
    }

    private static void addPage(final List<PendingComponent.Container> pages,
                                final PendingComponent.Container source,
                                final List<PendingComponent> children) {
        if (!children.isEmpty())
            pages.add(source.withComponents(children));
    }

    private static List<PendingComponent> normalizeChildren(final List<PendingComponent> children,
                                                            final PendingComponent.Container containerContext) {
        final List<PendingComponent> result = new ArrayList<>();
        for (final PendingComponent child : children) {
            if (child instanceof PendingComponent.TextDisplay text) {
                result.addAll(splitText(text.content(), value -> ComponentLimits.codePoints(value)
                        <= ComponentLimits.MAX_TEXT_DISPLAY_CODE_POINTS_PER_MESSAGE).stream()
                        .map(PendingComponent.TextDisplay::new).toList());
            } else if (child instanceof PendingComponent.MediaGallery gallery) {
                result.addAll(normalizeGallery(gallery, containerContext));
            } else if (child instanceof PendingComponent.Section section) {
                final PendingComponent.Separator prefix = !result.isEmpty()
                        && result.getLast() instanceof PendingComponent.Separator separator ? separator : null;
                result.addAll(normalizeSection(section, containerContext, prefix));
            } else if (child instanceof PendingComponent.Container) {
                throw new IllegalArgumentException("Containers cannot be nested");
            } else {
                result.add(child);
            }
        }
        return result;
    }

    private static List<PendingComponent.MediaGallery> normalizeGallery(final PendingComponent.MediaGallery gallery,
                                                                       final PendingComponent.Container containerContext) {
        if (gallery.items().isEmpty())
            throw new IllegalArgumentException("A media gallery must contain at least one item");
        final List<PendingComponent.MediaGallery> result = new ArrayList<>();
        List<PendingComponent.MediaGalleryItem> current = new ArrayList<>();
        for (final PendingComponent.MediaGalleryItem source : gallery.items()) {
            final PendingComponent.MediaGalleryItem item = normalizeMediaItem(source);
            final List<PendingComponent.MediaGalleryItem> candidate = new ArrayList<>(current);
            candidate.add(item);
            final PendingComponent.MediaGallery candidateGallery = new PendingComponent.MediaGallery(candidate);
            final List<PendingComponent> message = containerContext == null ? List.of(candidateGallery)
                    : List.of(containerContext.withComponents(List.of(candidateGallery)));
            if (candidate.size() <= ComponentLimits.MAX_MEDIA_GALLERY_ITEMS && fit(message) != Fit.DOES_NOT_FIT) {
                current = candidate;
            } else {
                if (!current.isEmpty())
                    result.add(new PendingComponent.MediaGallery(current));
                current = new ArrayList<>();
                current.add(item);
            }
        }
        if (!current.isEmpty())
            result.add(new PendingComponent.MediaGallery(current));
        return result;
    }

    private static List<PendingComponent.Section> normalizeSection(final PendingComponent.Section section,
                                                                   final PendingComponent.Container containerContext,
                                                                   final PendingComponent.Separator prefix) {
        if (section.components().isEmpty())
            throw new IllegalArgumentException("A section must contain at least one text display");
        if (section.accessory() == null)
            throw new IllegalArgumentException("A section requires an accessory");
        final PendingComponent.Accessory accessory;
        if (section.accessory() instanceof PendingComponent.Thumbnail thumbnail) {
            accessory = new PendingComponent.Thumbnail(thumbnail.url(),
                    ComponentLimits.truncate(thumbnail.description(), ComponentLimits.MAX_MEDIA_DESCRIPTION),
                    thumbnail.spoiler());
        } else {
            throw new IllegalArgumentException("Unsupported section accessory: " + section.accessory());
        }

        final List<PendingComponent.TextDisplay> texts = new ArrayList<>();
        for (final PendingComponent.TextDisplay text : section.components()) {
            final Predicate<String> fitsAlone = value -> fit(sectionMessage(
                    new PendingComponent.Section(List.of(new PendingComponent.TextDisplay(value)), accessory),
                    containerContext, prefix)) != Fit.DOES_NOT_FIT;
            splitText(text.content(), fitsAlone).stream().map(PendingComponent.TextDisplay::new).forEach(texts::add);
        }

        final List<PendingComponent.Section> result = new ArrayList<>();
        List<PendingComponent.TextDisplay> current = new ArrayList<>();
        for (final PendingComponent.TextDisplay text : texts) {
            final List<PendingComponent.TextDisplay> candidate = new ArrayList<>(current);
            candidate.add(text);
            final PendingComponent.Section candidateSection = new PendingComponent.Section(candidate, accessory);
            if (candidate.size() <= ComponentLimits.MAX_SECTION_CHILDREN
                    && fit(sectionMessage(candidateSection, containerContext, prefix)) != Fit.DOES_NOT_FIT) {
                current = candidate;
            } else {
                result.add(new PendingComponent.Section(current, accessory));
                current = new ArrayList<>();
                current.add(text);
            }
        }
        if (!current.isEmpty())
            result.add(new PendingComponent.Section(current, accessory));
        return result;
    }

    private static List<PendingComponent> sectionMessage(final PendingComponent.Section section,
                                                         final PendingComponent.Container containerContext,
                                                         final PendingComponent.Separator prefix) {
        if (containerContext == null)
            return List.of(section);
        final List<PendingComponent> children = new ArrayList<>();
        if (prefix != null)
            children.add(prefix);
        children.add(section);
        return List.of(containerContext.withComponents(children));
    }

    private static PendingComponent.MediaGalleryItem normalizeMediaItem(final PendingComponent.MediaGalleryItem item) {
        return new PendingComponent.MediaGalleryItem(item.url(),
                ComponentLimits.truncate(item.description(), ComponentLimits.MAX_MEDIA_DESCRIPTION), item.spoiler());
    }

    static List<String> splitText(final String source, final Predicate<String> fits) {
        if (source == null || source.isEmpty())
            return List.of();
        final List<String> parts = new ArrayList<>();
        String remaining = source;
        while (!fits.test(remaining)) {
            int low = 1;
            int high = ComponentLimits.codePoints(remaining);
            while (low < high) {
                final int middle = low + (high - low + 1) / 2;
                if (fits.test(prefix(remaining, middle)))
                    low = middle;
                else
                    high = middle - 1;
            }
            if (low <= 0)
                throw new IllegalArgumentException("Text cannot fit in a Components V2 request");
            int split = prefix(remaining, low).lastIndexOf('\n');
            if (split > 0)
                split++;
            else
                split = remaining.offsetByCodePoints(0, low);
            parts.add(remaining.substring(0, split));
            remaining = remaining.substring(split);
        }
        if (!remaining.isEmpty())
            parts.add(remaining);
        return parts;
    }

    private static String prefix(final String value, final int codePoints) {
        return value.substring(0, value.offsetByCodePoints(0, Math.min(codePoints, ComponentLimits.codePoints(value))));
    }

    public static Fit fit(final List<PendingComponent> message) {
        if (!ComponentValidator.isValid(message))
            return Fit.DOES_NOT_FIT;
        final WebhookEffectiveCostEstimator.Result estimate = WebhookEffectiveCostEstimator.estimate(message);
        if (estimate instanceof WebhookEffectiveCostEstimator.Safe)
            return Fit.SAFE;
        if (estimate instanceof WebhookEffectiveCostEstimator.Indeterminate)
            return Fit.INDETERMINATE;
        return Fit.DOES_NOT_FIT;
    }

    public enum Fit {
        SAFE,
        INDETERMINATE,
        DOES_NOT_FIT
    }

    public static int componentCount(final List<PendingComponent> components) {
        return ComponentValidator.componentCount(components);
    }

    public static int textCodePoints(final List<PendingComponent> components) {
        return ComponentValidator.textCodePoints(components);
    }
}
