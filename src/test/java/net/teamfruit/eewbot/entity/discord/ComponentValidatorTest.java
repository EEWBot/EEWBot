package net.teamfruit.eewbot.entity.discord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentValidatorTest {

    @Test
    void validatesDocumentedMessageAndTextLimits() {
        List<PendingComponent> forty = new ArrayList<>();
        for (int i = 0; i < ComponentLimits.MAX_COMPONENTS_PER_MESSAGE; i++)
            forty.add(new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL));
        assertThat(ComponentValidator.isValid(forty)).isTrue();

        forty.add(new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL));
        assertThat(ComponentValidator.isValid(forty)).isFalse();
        assertThat(ComponentValidator.isValid(List.of(new PendingComponent.Text("a".repeat(4000))))).isTrue();
        assertThat(ComponentValidator.isValid(List.of(new PendingComponent.Text("a".repeat(4001))))).isFalse();
    }

    @Test
    void validatesSectionGalleryAndNestingConstraints() {
        PendingComponent.Thumbnail thumbnail = new PendingComponent.Thumbnail("https://example.com/a.png", null, false);
        PendingComponent.Section emptySection = new PendingComponent.Section(List.of(), thumbnail);
        PendingComponent.MediaGallery emptyGallery = new PendingComponent.MediaGallery(List.of());
        PendingComponent.Container nested = new PendingComponent.Container(List.of(
                new PendingComponent.Container(List.of(new PendingComponent.Text("nested")), null, false)), null, false);

        assertThat(ComponentValidator.isValid(List.of(emptySection))).isFalse();
        assertThat(ComponentValidator.isValid(List.of(emptyGallery))).isFalse();
        assertThat(ComponentValidator.isValid(List.of(nested))).isFalse();
    }
}
