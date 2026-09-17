package net.teamfruit.eewbot.entity.discord;

import java.util.List;

/**
 * Validates the documented Discord constraints for the Components V2 subset used by EEWBot.
 */
public final class ComponentValidator {

    private ComponentValidator() {
    }

    public static boolean isValid(final List<PendingComponent> components) {
        return components != null
                && componentCount(components) <= ComponentLimits.MAX_COMPONENTS_PER_MESSAGE
                && textCodePoints(components) <= ComponentLimits.MAX_TEXT_DISPLAY_CODE_POINTS_PER_MESSAGE
                && components.stream().allMatch(component -> isValid(component, false));
    }

    public static int componentCount(final List<PendingComponent> components) {
        return components.stream().mapToInt(ComponentValidator::componentCount).sum();
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

    private static boolean isValid(final PendingComponent component, final boolean insideContainer) {
        if (component instanceof PendingComponent.Text text)
            return text.content() != null && !text.content().isEmpty();
        if (component instanceof PendingComponent.Separator separator)
            return separator.spacing() != null;
        if (component instanceof PendingComponent.MediaGallery gallery)
            return gallery.items().size() >= 1
                    && gallery.items().size() <= ComponentLimits.MAX_MEDIA_GALLERY_ITEMS
                    && gallery.items().stream().allMatch(ComponentValidator::isValid);
        if (component instanceof PendingComponent.Section section)
            return section.children().size() >= 1
                    && section.children().size() <= ComponentLimits.MAX_SECTION_CHILDREN
                    && section.children().stream().allMatch(text -> isValid(text, insideContainer))
                    && isValid(section.accessory());
        if (component instanceof PendingComponent.Container container)
            return !insideContainer
                    && !container.children().isEmpty()
                    && container.children().stream().allMatch(child -> isValid(child, true));
        return false;
    }

    private static boolean isValid(final PendingComponent.Accessory accessory) {
        if (accessory instanceof PendingComponent.Thumbnail thumbnail)
            return thumbnail.url() != null && !thumbnail.url().isBlank()
                    && ComponentLimits.codePoints(thumbnail.description()) <= ComponentLimits.MAX_MEDIA_DESCRIPTION;
        return false;
    }

    private static boolean isValid(final PendingComponent.MediaItem item) {
        return item.url() != null && !item.url().isBlank()
                && ComponentLimits.codePoints(item.description()) <= ComponentLimits.MAX_MEDIA_DESCRIPTION;
    }
}
