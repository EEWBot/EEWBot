package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.Codecs;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentPackerTest {

    private static PendingComponent.Container container(final List<PendingComponent> children) {
        return new PendingComponent.Container(children, 0xff0000, false);
    }

    private static String allText(final List<List<PendingComponent>> messages) {
        StringBuilder result = new StringBuilder();
        for (List<PendingComponent> message : messages)
            collectText(message, result);
        return result.toString();
    }

    private static void collectText(final List<PendingComponent> components, final StringBuilder result) {
        for (PendingComponent component : components) {
            if (component instanceof PendingComponent.Text text)
                result.append(text.content());
            else if (component instanceof PendingComponent.Container nested)
                collectText(nested.children(), result);
            else if (component instanceof PendingComponent.Section section)
                section.children().forEach(text -> result.append(text.content()));
        }
    }

    @Test
    void acceptsExactlyFortyComponentsAndPaginatesFortyOne() {
        List<PendingComponent> thirtyNineChildren = new ArrayList<>();
        for (int i = 0; i < 39; i++)
            thirtyNineChildren.add(new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL));

        List<List<PendingComponent>> exactly = ComponentPacker.pack(List.of(container(thirtyNineChildren)));
        assertThat(exactly).hasSize(1);
        assertThat(ComponentPacker.componentCount(exactly.getFirst())).isEqualTo(40);

        thirtyNineChildren.add(new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL));
        assertThat(ComponentPacker.pack(List.of(container(thirtyNineChildren)))).hasSize(2);
    }

    @Test
    void acceptsTextLimitAndPaginatesLimitPlusOneWithoutLoss() {
        String exact = "a".repeat(ComponentLimits.MAX_TEXT_DISPLAY_CODE_POINTS_PER_MESSAGE);
        assertThat(ComponentPacker.pack(List.of(container(List.of(new PendingComponent.Text(exact)))))).hasSize(1);

        String over = exact + "b";
        List<List<PendingComponent>> pages = ComponentPacker.pack(List.of(container(List.of(new PendingComponent.Text(over)))));
        assertThat(pages).hasSize(2);
        assertThat(allText(pages)).isEqualTo(over);
    }

    @Test
    void countsUnicodeCodePointsAndPreservesFourByteCharacters() {
        String emoji = "😀".repeat(4001);
        List<List<PendingComponent>> pages = ComponentPacker.pack(List.of(container(List.of(new PendingComponent.Text(emoji)))));
        assertThat(pages).hasSizeGreaterThan(1);
        assertThat(allText(pages)).isEqualTo(emoji);
        pages.forEach(page -> assertThat(ComponentPacker.textCodePoints(page)).isLessThanOrEqualTo(4000));
    }

    @Test
    void jsonEscapingDoesNotAffectEffectiveCost() {
        String plain = "a".repeat(3386);
        String escaped = "\"".repeat(3386);
        WebhookEffectiveCostEstimator.Safe plainEstimate = (WebhookEffectiveCostEstimator.Safe)
                WebhookEffectiveCostEstimator.estimate(List.of(new PendingComponent.Text(plain)));
        WebhookEffectiveCostEstimator.Safe escapedEstimate = (WebhookEffectiveCostEstimator.Safe)
                WebhookEffectiveCostEstimator.estimate(List.of(new PendingComponent.Text(escaped)));

        assertThat(escapedEstimate.effectiveCost()).isEqualTo(plainEstimate.effectiveCost());
        int plainJsonBytes = Codecs.GSON.toJson(new DiscordComponent.TextDisplay(10, plain))
                .getBytes(StandardCharsets.UTF_8).length;
        int escapedJsonBytes = Codecs.GSON.toJson(new DiscordComponent.TextDisplay(10, escaped))
                .getBytes(StandardCharsets.UTF_8).length;
        assertThat(escapedJsonBytes).isGreaterThan(plainJsonBytes);
    }

    @Test
    void paginatesAtObservedEffectiveCostBoundaryWithoutLosingText() {
        String exact = "あ".repeat(3386);
        String over = exact + "a";

        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(new PendingComponent.Text(exact))))
                .isEqualTo(new WebhookEffectiveCostEstimator.Safe(10170));
        assertThat(WebhookEffectiveCostEstimator.estimate(List.of(new PendingComponent.Text(over))))
                .isEqualTo(new WebhookEffectiveCostEstimator.TooLarge(10171));
        assertThat(ComponentPacker.fit(List.of(new PendingComponent.Text(exact))))
                .isEqualTo(ComponentPacker.Fit.SAFE);
        assertThat(ComponentPacker.fit(List.of(new PendingComponent.Text(over))))
                .isEqualTo(ComponentPacker.Fit.DOES_NOT_FIT);
        assertThat(ComponentPacker.pack(List.of(new PendingComponent.Text(exact)))).hasSize(1);
        List<List<PendingComponent>> pages = ComponentPacker.pack(List.of(new PendingComponent.Text(over)));
        assertThat(pages).hasSize(2);
        assertThat(allText(pages)).isEqualTo(over);
    }

    @Test
    void enforcesSectionChildLimitAndPaginatesAdditionalChildren() {
        PendingComponent.Thumbnail thumbnail = new PendingComponent.Thumbnail(
                "https://example.com/a.png", null, false);
        PendingComponent.Section exact = new PendingComponent.Section(List.of(
                new PendingComponent.Text("a"), new PendingComponent.Text("b"), new PendingComponent.Text("c")), thumbnail);
        PendingComponent.Section over = new PendingComponent.Section(List.of(
                new PendingComponent.Text("a"), new PendingComponent.Text("b"),
                new PendingComponent.Text("c"), new PendingComponent.Text("d")), thumbnail);
        PendingComponent.Container exactPacked = (PendingComponent.Container) ComponentPacker
                .pack(List.of(container(List.of(exact)))).getFirst().getFirst();
        assertThat(((PendingComponent.Section) exactPacked.children().getFirst()).children()).hasSize(3);
        List<List<PendingComponent>> overPages = ComponentPacker.pack(List.of(container(List.of(over))));
        assertThat(overPages).hasSize(2);
        assertThat(overPages).allSatisfy(page -> assertThat(
                ((PendingComponent.Container) page.getFirst()).children()).hasSize(1));
        PendingComponent.Container overPacked = (PendingComponent.Container) overPages.getFirst().getFirst();
        assertThat(((PendingComponent.Section) overPacked.children().getFirst()).children()).hasSize(3);
    }

    @Test
    void rejectsEmptySectionsAndNestedContainers() {
        PendingComponent.Section section = new PendingComponent.Section(List.of(),
                new PendingComponent.Thumbnail("https://example.com/a.png", null, false));
        assertThatThrownBy(() -> ComponentPacker.pack(List.of(container(List.of(section)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComponentPacker.pack(List.of(container(List.of(container(List.of(
                new PendingComponent.Text("nested"))))))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitsMediaGalleriesAtTenItems() {
        List<PendingComponent.MediaItem> items = new ArrayList<>();
        for (int i = 0; i < 11; i++)
            items.add(new PendingComponent.MediaItem("https://example.com/" + i + ".png", "image " + i, false));
        List<List<PendingComponent>> messages = ComponentPacker.pack(List.of(container(List.of(new PendingComponent.MediaGallery(items)))));
        assertThat(messages).hasSize(2);
        PendingComponent.Container first = (PendingComponent.Container) messages.getFirst().getFirst();
        PendingComponent.Container second = (PendingComponent.Container) messages.getLast().getFirst();
        assertThat(first.children()).hasSize(1);
        assertThat(second.children()).hasSize(1);
        assertThat(((PendingComponent.MediaGallery) first.children().getFirst()).items()).hasSize(10);
        assertThat(((PendingComponent.MediaGallery) second.children().getFirst()).items()).hasSize(1);
    }

    @Test
    void isolatesUnknownMediaWithItsSeparatorAndPreservesOrder() {
        PendingComponent.MediaGallery gallery = new PendingComponent.MediaGallery(List.of(
                new PendingComponent.MediaItem("https://example.com/image.png", "described", false)));
        List<List<PendingComponent>> messages = ComponentPacker.pack(List.of(container(List.of(
                new PendingComponent.Text("あ".repeat(3000)),
                new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL),
                gallery,
                new PendingComponent.Text("footer")))));

        assertThat(messages).hasSize(3);
        PendingComponent.Container before = (PendingComponent.Container) messages.get(0).getFirst();
        PendingComponent.Container isolated = (PendingComponent.Container) messages.get(1).getFirst();
        PendingComponent.Container after = (PendingComponent.Container) messages.get(2).getFirst();
        assertThat(before.children()).containsExactly(new PendingComponent.Text("あ".repeat(3000)));
        assertThat(isolated.children()).containsExactly(
                new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL), gallery);
        assertThat(after.children()).containsExactly(new PendingComponent.Text("footer"));
        assertThat(ComponentPacker.fit(messages.get(0))).isEqualTo(ComponentPacker.Fit.SAFE);
        assertThat(ComponentPacker.fit(messages.get(1))).isEqualTo(ComponentPacker.Fit.INDETERMINATE);
        assertThat(ComponentPacker.fit(messages.get(2))).isEqualTo(ComponentPacker.Fit.SAFE);
    }

    @Test
    void doesNotCreateEmptyContainerWhenMovingSeparatorToUnknownMedia() {
        PendingComponent.MediaGallery gallery = new PendingComponent.MediaGallery(List.of(
                new PendingComponent.MediaItem("https://example.com/image.png", "described", false)));
        List<List<PendingComponent>> messages = ComponentPacker.pack(List.of(container(List.of(
                new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL), gallery))));

        assertThat(messages).hasSize(1);
        PendingComponent.Container isolated = (PendingComponent.Container) messages.getFirst().getFirst();
        assertThat(isolated.children()).containsExactly(
                new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL), gallery);
    }

    @Test
    void movesSeparatorAfterSplittingAnOversizedUnknownSection() {
        String text = "あ".repeat(4000);
        PendingComponent.Thumbnail thumbnail = new PendingComponent.Thumbnail(
                "https://example.com/thumb.png", null, false);
        PendingComponent.Section section = new PendingComponent.Section(
                List.of(new PendingComponent.Text(text)), thumbnail);
        List<List<PendingComponent>> messages = ComponentPacker.pack(List.of(container(List.of(
                new PendingComponent.Separator(true, PendingComponent.Spacing.SMALL), section))));

        assertThat(messages).hasSize(2);
        PendingComponent.Container first = (PendingComponent.Container) messages.getFirst().getFirst();
        PendingComponent.Container second = (PendingComponent.Container) messages.getLast().getFirst();
        assertThat(first.children().getFirst()).isInstanceOf(PendingComponent.Separator.class);
        assertThat(first.children().getLast()).isInstanceOf(PendingComponent.Section.class);
        assertThat(second.children()).hasSize(1);
        assertThat(second.children().getFirst()).isInstanceOf(PendingComponent.Section.class);
        assertThat(allText(messages)).isEqualTo(text);
    }

    @Test
    void paginatesVeryLongJmaCommentsAndManyRegionsWithoutDroppingData() {
        String comment = "長い防災情報コメント。".repeat(1000);
        String regions = String.join("　", java.util.Collections.nCopies(1200, "予報区"));
        String source = comment + regions;
        List<List<PendingComponent>> messages = ComponentPacker.pack(List.of(container(List.of(new PendingComponent.Text(source)))));
        assertThat(messages).hasSizeGreaterThan(1);
        assertThat(allText(messages)).isEqualTo(source);
    }
}
