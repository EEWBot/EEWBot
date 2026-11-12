package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.IntensityEmojiRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EEWThumbnailTest {

    private static final long EMOJI_ID_BASE = 123456789012345670L;
    private final IntensityEmojiRegistry emojis = mock(IntensityEmojiRegistry.class);
    private final EmbedContext context = new EmbedContext(new RendererQueryFactory(null, null), new QuakeInfoStore(),
            new I18n("ja_jp"), this.emojis);

    @BeforeEach
    void setUp() {
        when(this.emojis.getImageUrl(any())).thenAnswer(call -> {
            SeismicIntensity intensity = call.getArgument(0);
            return intensity == SeismicIntensity.UNKNOWN ? Optional.empty()
                    : Optional.of(emojiUrl(intensity));
        });
    }

    @ParameterizedTest
    @EnumSource(value = SeismicIntensity.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
    void forecastThumbnailUsesTheCorrespondingIntensityEmoji(SeismicIntensity intensity) throws Exception {
        String xml = fixture("dmdata/eew/forecast_normal.xml")
                .replace("<From>4</From>", "<From>" + intensity.getSymbolIntensity() + "</From>")
                .replace("<To>4</To>", "<To>" + intensity.getSymbolIntensity() + "</To>");
        EEW report = Codecs.XML_MAPPER.readValue(xml, VXSE45Impl.class);

        assertThumbnail(report, emojiUrl(intensity));
    }

    @ParameterizedTest
    @CsvSource({
            "jmaxml/vxse43/warning.xml, SIX_MINUS",
            "dmdata/eew/final.xml, FIVE_PLUS"
    })
    void warningAndFinalReportsAlsoUseTheForecastIntensityEmoji(String fixture, SeismicIntensity intensity) throws Exception {
        EEW report = Codecs.XML_MAPPER.readValue(fixture(fixture), VXSE45Impl.class);

        assertThumbnail(report, emojiUrl(intensity));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"不明", "unknown"})
    void unknownForecastIntensityHasNoThumbnail(String from) throws Exception {
        String xml = fixture("dmdata/eew/forecast_normal.xml").replace("<From>4</From>",
                from == null ? "" : "<From>" + from + "</From>");
        EEW report = Codecs.XML_MAPPER.readValue(xml, VXSE45Impl.class);

        assertThumbnail(report, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dmdata/eew/deep.xml", "dmdata/eew/cancel.xml"})
    void missingForecastAndCancellationHaveNoThumbnail(String fixture) throws Exception {
        EEW report = Codecs.XML_MAPPER.readValue(fixture(fixture), VXSE45Impl.class);

        assertThumbnail(report, null);
    }

    @Test
    void unavailableEmojiImageHasNoThumbnail() throws Exception {
        EEW report = Codecs.XML_MAPPER.readValue(fixture("dmdata/eew/forecast_normal.xml"), VXSE45Impl.class);
        when(this.emojis.getImageUrl(SeismicIntensity.FOUR)).thenReturn(Optional.empty());

        assertThumbnail(report, null);
    }

    private void assertThumbnail(EEW report, String expected) {
        var webhooks = report.createWebhooks("ja_jp", this.context);
        assertThat(webhooks).hasSize(1);
        assertThat(webhooks.getFirst().embeds).hasSize(1);
        var thumbnail = webhooks.getFirst().embeds.getFirst().thumbnail;
        assertThat(thumbnail == null ? null : thumbnail.url).isEqualTo(expected);

        var messages = report.createMessages("ja_jp", this.context);
        assertThat(messages).hasSize(1);
        var embeds = messages.getFirst().embedsOrElse(List.of());
        assertThat(embeds).hasSize(1);
        assertThat(embeds.getFirst().thumbnailOrElse(null)).isEqualTo(expected);
    }

    private static String emojiUrl(SeismicIntensity intensity) {
        return "https://cdn.discordapp.com/emojis/" + (EMOJI_ID_BASE + intensity.getCode()) + ".webp";
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources", name));
    }
}
