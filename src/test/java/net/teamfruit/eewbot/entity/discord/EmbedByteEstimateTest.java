package net.teamfruit.eewbot.entity.discord;

import discord4j.rest.util.Color;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.discord.PendingEmbed.PendingField;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedByteEstimateTest {

    private static String serialize(final List<PendingEmbed> message) {
        return Codecs.GSON.toJson(DiscordWebhook.builder()
                .embeds(EmbedRenderer.toDiscordEmbeds(message)).build());
    }

    @Test
    void jsonEscapesDoNotConsumeTheDecodedTextBudget() {
        final PendingEmbed source = PendingEmbed.empty().withDescription("<&='\"\\\n\u2028\u2029".repeat(300));
        final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(source));

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getFirst().description()).isEqualTo(source.description());
        assertThat(serialize(messages.getFirst()).getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThan(EmbedWebhookCost.OBSERVED_BUDGET);
        assertThat(EmbedWebhookCost.fits(messages.getFirst())).isTrue();
    }

    @Test
    void preservesDescriptionAcrossByteAndCharacterBoundaries() {
        for (final String description : List.of("あ".repeat(9000), "😀".repeat(5000),
                "line\n".repeat(2000), "x".repeat(9000))) {
            final List<List<PendingEmbed>> messages = EmbedPacker.pack(List.of(
                    new PendingEmbed("title", description, null, Instant.EPOCH, Color.RED,
                            "footer", null, null, null, "author", null, null, List.of())));
            final List<PendingEmbed> pages = messages.stream().flatMap(List::stream).toList();
            assertThat(pages.stream().map(PendingEmbed::description).filter(java.util.Objects::nonNull)
                    .collect(Collectors.joining())).isEqualTo(description);
            assertThat(pages.getFirst().title()).isEqualTo("title");
            assertThat(pages.getLast().footerText()).isEqualTo("footer");
            for (final List<PendingEmbed> message : messages) {
                assertThat(EmbedWebhookCost.fits(message)).isTrue();
                assertThat(message.stream().mapToInt(PendingEmbed::charCount).sum()).isLessThanOrEqualTo(6000);
                message.forEach(page -> assertThat(DiscordLimits.codePoints(page.description())).isLessThanOrEqualTo(4096));
            }
        }
    }

    @Test
    void preservesFieldsWhenTheDescriptionConsumesTheFirstPage() {
        final String description = "あ".repeat(4000);
        final String value = "😀\n地名".repeat(700);
        final PendingEmbed source = PendingEmbed.empty().withDescription(description)
                .withFields(List.of(new PendingField("地域", value, true)));
        final List<PendingEmbed> pages = EmbedPacker.pack(List.of(source)).stream().flatMap(List::stream).toList();
        assertThat(pages.stream().map(PendingEmbed::description).filter(java.util.Objects::nonNull)
                .collect(Collectors.joining())).isEqualTo(description);
        final List<PendingField> fields = pages.stream().flatMap(page -> page.fields().stream()).toList();
        assertThat(fields.stream().map(PendingField::value).collect(Collectors.joining())).isEqualTo(value);
        assertThat(fields.getFirst().name()).isEqualTo("地域");
        fields.forEach(field -> {
            assertThat(DiscordLimits.codePoints(field.value())).isLessThanOrEqualTo(1024);
            assertThat(field.inline()).isTrue();
        });
    }

    @Test
    void splitsBeforeMeasuredFailureBoundaries() {
        final List<PendingEmbed> sources = new ArrayList<>();
        sources.add(PendingEmbed.empty().withDescription("あ".repeat(4000)));
        final List<PendingField> fields = new ArrayList<>();
        for (int i = 0; i < 25; i++)
            fields.add(new PendingField("名".repeat(256), "あ".repeat(1024), i % 2 == 0));
        sources.add(PendingEmbed.empty().withFields(fields));
        final List<List<PendingEmbed>> messages = EmbedPacker.pack(sources);
        assertThat(messages).hasSizeGreaterThan(1);
        for (final List<PendingEmbed> message : messages)
            assertThat(EmbedWebhookCost.fits(message)).isTrue();
        assertThat(messages.stream().flatMap(List::stream).flatMap(page -> page.fields().stream()).toList())
                .containsExactlyElementsOf(fields);
    }

    @Test
    void movesTailToItsOwnPageWithoutTrimmingTheLastField() {
        final String image = "https://example.com/" + "あ".repeat(2000);
        final List<PendingField> fields = List.of(new PendingField("name", "😀".repeat(1024), false));
        final PendingEmbed source = new PendingEmbed(null, null, null, Instant.EPOCH, Color.RED,
                "f".repeat(2048), null, image, null, "author", null, null, fields);
        final List<PendingEmbed> pages = EmbedPacker.pack(List.of(source)).stream().flatMap(List::stream).toList();
        assertThat(pages).hasSize(2);
        assertThat(pages.getFirst().fields()).containsExactlyElementsOf(fields);
        assertThat(pages.getLast().image()).isEqualTo(image);
        assertThat(pages.getLast().footerText()).isEqualTo(source.footerText());
    }
}
