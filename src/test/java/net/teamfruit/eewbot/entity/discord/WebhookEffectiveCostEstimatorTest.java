package net.teamfruit.eewbot.entity.discord;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEffectiveCostEstimatorTest {

    @Test
    void estimatesCharacterizedTextSeparatorAndContainerCosts() {
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(
                new PendingComponent.Text("あ"),
                new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL))))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(26));

        PendingComponent.Container container = new PendingComponent.Container(List.of(
                new PendingComponent.Text("あ"),
                new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL)), 0xff0000, true);
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(container)))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(42));
    }

    @Test
    void usesWorstKnownCostForCharacterizedMediaGallery() {
        PendingComponent.MediaItem item = new PendingComponent.MediaItem(
                "https://embed-bug-tester.yr32.net/1234567890", null, false);

        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(
                new PendingComponent.MediaGallery(List.of(item)))))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(172));
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(
                new PendingComponent.MediaGallery(List.of(item, item)))))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(335));
    }

    @Test
    void reportsUncharacterizedMediaAndThumbnailAsIndeterminate() {
        PendingComponent.MediaGallery gallery = new PendingComponent.MediaGallery(List.of(
                new PendingComponent.MediaItem("https://example.com/image.png", null, false)));
        PendingComponent.Section section = new PendingComponent.Section(List.of(new PendingComponent.Text("text")),
                new PendingComponent.Thumbnail("https://example.com/thumb.png", null, false));

        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(gallery)))
                .isInstanceOf(WebhookEffectiveCostEstimator.Indeterminate.class);
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(section)))
                .isInstanceOf(WebhookEffectiveCostEstimator.Indeterminate.class);
    }
}
