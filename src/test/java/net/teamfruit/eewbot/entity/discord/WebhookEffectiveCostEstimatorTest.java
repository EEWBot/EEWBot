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
        // The 44 byte URL was measured at 163 per item; the model charges 164, one byte on the safe side.
        PendingComponent.MediaItem item = new PendingComponent.MediaItem(
                "https://embed-bug-tester.yr32.net/1234567890", null, false);

        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(
                new PendingComponent.MediaGallery(List.of(item)))))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(173));
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(
                new PendingComponent.MediaGallery(List.of(item, item)))))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(337));
    }

    @Test
    void scalesPlainMediaCostWithUrlLengthInsideAndOutsideContainers() {
        String shortUrl = "https://example.com/" + "a".repeat(107) + ".webp";
        String longUrl = "https://example.com/" + "a".repeat(108) + ".webp";
        assertThat(ComponentLimits.utf8Bytes(shortUrl)).isEqualTo(132);

        PendingComponent.MediaGallery shortGallery = new PendingComponent.MediaGallery(List.of(
                new PendingComponent.MediaItem(shortUrl, null, false)));
        PendingComponent.MediaGallery longGallery = new PendingComponent.MediaGallery(List.of(
                new PendingComponent.MediaItem(longUrl, null, false)));

        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(shortGallery)))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(9 + 132 + 120 + 1));
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(longGallery)))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(9 + 133 + 120 + 1));

        PendingComponent.Container container = new PendingComponent.Container(List.of(shortGallery), null, false);
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(container)))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(13 + 9 + 132 + 120 + 1));
    }

    @Test
    void addsNoLengthPrefixByteBelowTheThreshold() {
        String url = "https://example.com/" + "a".repeat(102) + ".webp";
        assertThat(ComponentLimits.utf8Bytes(url)).isEqualTo(127);
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(new PendingComponent.MediaGallery(List.of(
                new PendingComponent.MediaItem(url, null, false))))))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(9 + 127 + 120));
    }

    @Test
    void reportsUncharacterizedMediaAndThumbnailAsIndeterminate() {
        PendingComponent.MediaGallery gallery = new PendingComponent.MediaGallery(List.of(
                new PendingComponent.MediaItem("https://example.com/image.png", "described", false)));
        PendingComponent.Section section = new PendingComponent.Section(List.of(new PendingComponent.Text("text")),
                new PendingComponent.Thumbnail("https://example.com/thumb.png", null, false));

        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(gallery)))
                .isInstanceOf(WebhookEffectiveCostEstimator.Indeterminate.class);
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(section)))
                .isInstanceOf(WebhookEffectiveCostEstimator.Indeterminate.class);
    }

    @Test
    void preservesKnownTextMinimumInsideUncharacterizedSection() {
        PendingComponent.Thumbnail thumbnail = new PendingComponent.Thumbnail(
                "https://example.com/thumb.png", null, false);
        PendingComponent.Section atBoundary = new PendingComponent.Section(
                List.of(new PendingComponent.Text("あ".repeat(3390))), thumbnail);
        PendingComponent.Section overBoundary = new PendingComponent.Section(
                List.of(new PendingComponent.Text("あ".repeat(3390) + "a")), thumbnail);

        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(atBoundary)))
                .isEqualTo(new WebhookEffectiveCostEstimator.Indeterminate(10170));
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(overBoundary)))
                .isEqualTo(new WebhookEffectiveCostEstimator.TooLarge(10171));
    }
}
