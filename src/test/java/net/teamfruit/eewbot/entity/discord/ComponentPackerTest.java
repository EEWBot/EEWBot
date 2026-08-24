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
            thirtyNineChildren.add(new PendingComponent.Separator(false, PendingComponent.Spacing.SMALL));

        List<List<PendingComponent>> exactly = ComponentPacker.pack(List.of(container(thirtyNineChildren)));
        assertThat(exactly).hasSize(1);
        assertThat(ComponentPacker.componentCount(exactly.getFirst())).isEqualTo(40);

        thirtyNineChildren.add(new PendingComponent.Separator(false, PendingComponent.Spacing.SMALL));
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
    void accountsForJsonEscapingAndUtf8RequestBytes() {
        String escaped = "\\\"\n😀".repeat(1800);
        List<List<PendingComponent>> pages = ComponentPacker.pack(List.of(container(List.of(new PendingComponent.Text(escaped)))));
        assertThat(allText(pages)).isEqualTo(escaped);
        pages.forEach(page -> {
            DiscordWebhook webhook = DiscordWebhook.builder().components(ComponentRenderer.toWebhook(page)).build();
            int bytes = Codecs.GSON.toJson(webhook).getBytes(StandardCharsets.UTF_8).length;
            assertThat(bytes).isLessThanOrEqualTo(ComponentLimits.MAX_PACKED_COMPONENT_BODY_BYTES);
        });
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
        PendingComponent.Container overPacked = (PendingComponent.Container) ComponentPacker
                .pack(List.of(container(List.of(over)))).getFirst().getFirst();
        assertThat(((PendingComponent.Section) exactPacked.children().getFirst()).children()).hasSize(3);
        assertThat(overPacked.children()).hasSize(2);
        assertThat(overPacked.children()).allSatisfy(component -> assertThat(
                ((PendingComponent.Section) component).children()).hasSizeLessThanOrEqualTo(3));
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
        PendingComponent.Container packed = (PendingComponent.Container) messages.getFirst().getFirst();
        assertThat(packed.children()).hasSize(2);
        assertThat(((PendingComponent.MediaGallery) packed.children().getFirst()).items()).hasSize(10);
        assertThat(((PendingComponent.MediaGallery) packed.children().getLast()).items()).hasSize(1);
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
