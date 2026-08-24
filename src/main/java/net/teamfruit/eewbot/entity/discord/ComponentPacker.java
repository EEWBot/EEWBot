package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.Codecs;

import java.nio.charset.StandardCharsets;
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
            } else if (component instanceof PendingComponent.Text text) {
                for (final String part : splitText(text.content(), value -> fits(List.of(new PendingComponent.Text(value)))))
                    append(messages, new PendingComponent.Text(part));
            } else if (component instanceof PendingComponent.Section section) {
                for (final PendingComponent normalized : normalizeSection(section))
                    append(messages, normalized);
            } else if (component instanceof PendingComponent.MediaGallery gallery) {
                for (final PendingComponent normalized : normalizeChildren(List.of(gallery)))
                    append(messages, normalized);
            } else {
                append(messages, component);
            }
        }
        return messages;
    }

    private static void append(final List<List<PendingComponent>> messages, final PendingComponent component) {
        if (!messages.isEmpty()) {
            final List<PendingComponent> candidate = new ArrayList<>(messages.getLast());
            candidate.add(component);
            if (fits(candidate)) {
                messages.set(messages.size() - 1, List.copyOf(candidate));
                return;
            }
        }
        if (!fits(List.of(component)))
            throw new IllegalArgumentException("A component cannot be represented within Discord's message limits: " + component);
        messages.add(List.of(component));
    }

    private static List<PendingComponent.Container> paginate(final PendingComponent.Container source) {
        final List<PendingComponent> normalized = normalizeChildren(source.children());
        final List<PendingComponent.Container> pages = new ArrayList<>();
        List<PendingComponent> current = new ArrayList<>();
        for (final PendingComponent child : normalized) {
            final List<PendingComponent> candidate = new ArrayList<>(current);
            candidate.add(child);
            if (fits(List.of(source.withChildren(candidate)))) {
                current = candidate;
                continue;
            }
            if (!current.isEmpty()) {
                pages.add(source.withChildren(current));
                current = new ArrayList<>();
            }
            if (fits(List.of(source.withChildren(List.of(child))))) {
                current.add(child);
                continue;
            }
            if (child instanceof PendingComponent.Text text) {
                final Predicate<String> predicate = value -> fits(List.of(source.withChildren(List.of(new PendingComponent.Text(value)))));
                for (final String part : splitText(text.content(), predicate)) {
                    if (!current.isEmpty()) {
                        pages.add(source.withChildren(current));
                        current = new ArrayList<>();
                    }
                    current.add(new PendingComponent.Text(part));
                }
                continue;
            }
            throw new IllegalArgumentException("A container child cannot be represented within Discord's limits: " + child);
        }
        if (!current.isEmpty())
            pages.add(source.withChildren(current));
        return pages;
    }

    private static List<PendingComponent> normalizeChildren(final List<PendingComponent> children) {
        final List<PendingComponent> result = new ArrayList<>();
        for (final PendingComponent child : children) {
            if (child instanceof PendingComponent.Text text) {
                result.addAll(splitText(text.content(), value -> ComponentLimits.codePoints(value)
                        <= ComponentLimits.MAX_TEXT_DISPLAY_CODE_POINTS_PER_MESSAGE).stream()
                        .map(PendingComponent.Text::new).toList());
            } else if (child instanceof PendingComponent.MediaGallery gallery) {
                for (int start = 0; start < gallery.items().size(); start += ComponentLimits.MAX_MEDIA_GALLERY_ITEMS) {
                    final int end = Math.min(gallery.items().size(), start + ComponentLimits.MAX_MEDIA_GALLERY_ITEMS);
                    result.add(new PendingComponent.MediaGallery(gallery.items().subList(start, end).stream()
                            .map(ComponentPacker::normalizeMediaItem).toList()));
                }
            } else if (child instanceof PendingComponent.Section section) {
                result.addAll(normalizeSection(section));
            } else if (child instanceof PendingComponent.Container) {
                throw new IllegalArgumentException("Containers cannot be nested");
            } else {
                result.add(child);
            }
        }
        return result;
    }

    private static List<PendingComponent.Section> normalizeSection(final PendingComponent.Section section) {
        if (section.children().isEmpty())
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

        final List<PendingComponent.Text> texts = new ArrayList<>();
        for (final PendingComponent.Text text : section.children()) {
            final Predicate<String> fitsAlone = value -> fits(List.of(new PendingComponent.Container(List.of(
                    new PendingComponent.Section(List.of(new PendingComponent.Text(value)), accessory)), null, false)));
            splitText(text.content(), fitsAlone).stream().map(PendingComponent.Text::new).forEach(texts::add);
        }

        final List<PendingComponent.Section> result = new ArrayList<>();
        List<PendingComponent.Text> current = new ArrayList<>();
        for (final PendingComponent.Text text : texts) {
            final List<PendingComponent.Text> candidate = new ArrayList<>(current);
            candidate.add(text);
            final PendingComponent.Section candidateSection = new PendingComponent.Section(candidate, accessory);
            if (candidate.size() <= ComponentLimits.MAX_SECTION_CHILDREN && fits(List.of(
                    new PendingComponent.Container(List.of(candidateSection), null, false)))) {
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

    private static PendingComponent.MediaItem normalizeMediaItem(final PendingComponent.MediaItem item) {
        return new PendingComponent.MediaItem(item.url(),
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

    public static boolean fits(final List<PendingComponent> message) {
        if (componentCount(message) > ComponentLimits.MAX_COMPONENTS_PER_MESSAGE)
            return false;
        if (textCodePoints(message) > ComponentLimits.MAX_TEXT_DISPLAY_CODE_POINTS_PER_MESSAGE)
            return false;
        final DiscordWebhook webhook = DiscordWebhook.builder()
                .components(ComponentRenderer.toWebhook(message)).build();
        return Codecs.GSON.toJson(webhook).getBytes(StandardCharsets.UTF_8).length
                <= ComponentLimits.MAX_PACKED_COMPONENT_BODY_BYTES;
    }

    public static int componentCount(final List<PendingComponent> components) {
        return components.stream().mapToInt(ComponentPacker::componentCount).sum();
    }

    private static int componentCount(final PendingComponent component) {
        if (component instanceof PendingComponent.Container container)
            return 1 + componentCount(container.children());
        if (component instanceof PendingComponent.Section section)
            return 2 + section.children().size();
        return 1;
    }

    public static int textCodePoints(final List<PendingComponent> components) {
        int total = 0;
        for (final PendingComponent component : components) {
            if (component instanceof PendingComponent.Text text)
                total += ComponentLimits.codePoints(text.content());
            else if (component instanceof PendingComponent.Container container)
                total += textCodePoints(container.children());
            else if (component instanceof PendingComponent.Section section)
                total += section.children().stream().mapToInt(text -> ComponentLimits.codePoints(text.content())).sum();
        }
        return total;
    }
}
