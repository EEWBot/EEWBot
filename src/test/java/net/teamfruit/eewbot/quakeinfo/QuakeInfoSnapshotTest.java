package net.teamfruit.eewbot.quakeinfo;

import com.google.gson.JsonParser;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.*;
import net.teamfruit.eewbot.entity.jma.*;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.teamfruit.eewbot.testutil.QuakeReports.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuakeInfoSnapshotTest {
    static final I18n I18N = new I18n("ja_jp");
    static EmbedContext context(QuakeInfoStore store) {
        return new EmbedContext(new RendererQueryFactory("https://renderer.example.test", "synthetic-renderer-key"), store, I18N);
    }

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE51", "VXSE52", "VXSE53", "VXSE61"})
    void preservesFiveLocalizedPackedMessagesImagesAndMetadata(JMAXmlType type) throws Exception {
        var store = new QuakeInfoStore();
        var quake = (QuakeInfo) report(type, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1600));
        store.putReport(quake);
        var ctx = context(store);
        var snapshot = QuakeInfoSnapshot.render(store.getLatestReport().orElseThrow(), ctx, 7, 8);
        assertThat(snapshot.messages().keySet()).containsExactlyElementsOf(QuakeInfoSnapshot.LANGUAGES);
        StringBuilder manifest = new StringBuilder();
        for (String lang : QuakeInfoSnapshot.LANGUAGES) {
            var expected = EmbedPacker.pack(quake.createEmbeds(lang, ctx, () -> new I18nEmbedBuilder(lang, I18N)))
                    .stream().map(EmbedRenderer::toDiscordEmbeds).map(Codecs.GSON::toJson).toList();
            assertThat(snapshot.messages().get(lang)).isEqualTo(expected);
            for (int i = 0; i < expected.size(); i++) {
                String body = expected.get(i);
                manifest.append(lang).append(':').append(i).append(':').append(QuakeInfoSnapshot.sha256(body))
                        .append(':').append(body.getBytes(StandardCharsets.UTF_8).length).append('\n');
                assertThat(body).contains("timestamp", "footer");
            }
        }
        assertThat(snapshot.contentHash()).isEqualTo(QuakeInfoSnapshot.sha256(manifest.toString()));
        var meta = JsonParser.parseString(snapshot.metadataJson()).getAsJsonObject();
        assertThat(meta.get("publisherEpoch").getAsLong()).isEqualTo(7);
        assertThat(meta.get("publisherSequence").getAsLong()).isEqualTo(8);
        assertThat(meta.get("reportType").getAsString()).isEqualTo(type.name());
        if (type == JMAXmlType.VXSE51 || type == JMAXmlType.VXSE53)
            assertThat(snapshot.messages().get("ja_jp").getFirst()).contains("https://renderer.example.test");
    }

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE51", "VXSE52", "VXSE53", "VXSE61"})
    void rendersCorrectionsAndBodylessCancellations(JMAXmlType type) throws Exception {
        for (JMAInfoType infoType : List.of(JMAInfoType.訂正, JMAInfoType.取消)) {
            var quake = (QuakeInfo) report(type, 2, infoType, SeismicIntensity.THREE, START.plusSeconds(1600));
            var snapshot = QuakeInfoSnapshot.render(quake, context(new QuakeInfoStore()), 1, 1);
            var meta = JsonParser.parseString(snapshot.metadataJson()).getAsJsonObject();
            assertThat(meta.get("isCancel").getAsBoolean()).isEqualTo(infoType == JMAInfoType.取消);
            assertThat(meta.get("infoTypePriority").getAsInt()).isEqualTo(infoType == JMAInfoType.取消 ? 2 : 1);
            assertThat(snapshot.messages()).hasSize(5);
            if (infoType == JMAInfoType.取消) assertThat(snapshot.messages().get("ja_jp").getFirst()).contains("取り消");
        }
    }

    @Test
    void retainsMultipleMessageBoundariesAndAbortsIfAnyLanguageFails() throws Exception {
        var quake = spy((QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1600)));
        var huge = PendingEmbed.empty().withDescription("分割テスト\n".repeat(2000));
        doReturn(List.of(huge)).when(quake).createEmbeds(anyString(), any(), any());
        var snapshot = QuakeInfoSnapshot.render(quake, context(new QuakeInfoStore()), 1, 1);
        assertThat(snapshot.messages().get("ja_jp").size()).isGreaterThan(1);
        StringBuilder text = new StringBuilder();
        for (String body : snapshot.messages().get("ja_jp")) {
            for (var e : JsonParser.parseString(body).getAsJsonArray()) text.append(e.getAsJsonObject().get("description").getAsString());
        }
        assertThat(text.toString()).isEqualTo(huge.description());
        doThrow(new IllegalStateException("synthetic render error")).when(quake).createEmbeds(eq("ko_kp"), any(), any());
        assertThatThrownBy(() -> QuakeInfoSnapshot.render(quake, context(new QuakeInfoStore()), 1, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void referenceEnrichmentChangesRenderedContentWithoutChangingSelectedReport() throws Exception {
        var store = new QuakeInfoStore();
        var latest = (QuakeInfo) report(JMAXmlType.VXSE52, 1, JMAInfoType.発表, null, START.plusSeconds(1600));
        store.putReport(latest);
        var before = QuakeInfoSnapshot.render(latest, context(store), 1, 1);
        store.putReport((QuakeInfo) report(JMAXmlType.VXSE51, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1500)));
        var after = QuakeInfoSnapshot.render(latest, context(store), 1, 2);
        assertThat(store.getLatestReport()).containsSame(latest);
        assertThat(after.sourceKey()).isEqualTo(before.sourceKey());
        assertThat(after.contentHash()).isNotEqualTo(before.contentHash());
    }

    @Test
    void notifiesOutsideTheStoreLockAndListenerFailuresDoNotInterruptReportAcceptance() throws Exception {
        var store = new QuakeInfoStore();
        var calls = new AtomicInteger();
        store.addUpdateListener(() -> { assertThat(Thread.holdsLock(store)).isFalse(); calls.incrementAndGet(); throw new IllegalStateException(); });
        var quake = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1600));
        store.putReport(quake);
        assertThat(calls).hasValue(1);
        assertThat(store.getLatestReport()).containsSame(quake);
    }
}
