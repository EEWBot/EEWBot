package net.teamfruit.eewbot.quakeinfo;

import com.google.gson.JsonObject;
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

import java.util.ArrayList;
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

    static JsonObject root(QuakeInfoSnapshot snapshot) { return JsonParser.parseString(snapshot.body()).getAsJsonObject(); }
    static List<String> messages(QuakeInfoSnapshot snapshot, String lang) {
        List<String> result = new ArrayList<>();
        root(snapshot).getAsJsonObject("languages").getAsJsonArray(lang).forEach(message -> result.add(message.toString()));
        return result;
    }

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE51", "VXSE52", "VXSE53", "VXSE61"})
    void preservesFiveLocalizedPackedMessagesImagesAndMetadata(JMAXmlType type) throws Exception {
        var store = new QuakeInfoStore();
        var quake = (QuakeInfo) report(type, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1600));
        store.putReport(quake);
        var ctx = context(store);
        var snapshot = QuakeInfoSnapshot.render(store.getLatestReport().orElseThrow(), ctx);
        var root = root(snapshot);
        assertThat(root.keySet()).containsExactly("eventId", "reportType", "reportDateTime", "dateTime", "serial", "infoType", "languages");
        assertThat(root.getAsJsonObject("languages").keySet()).containsExactlyElementsOf(QuakeInfoSnapshot.LANGUAGES);
        for (String lang : QuakeInfoSnapshot.LANGUAGES) {
            var expected = EmbedPacker.pack(quake.createEmbeds(lang, ctx, () -> new I18nEmbedBuilder(lang, I18N)))
                    .stream().map(EmbedRenderer::toDiscordEmbeds).map(Codecs.GSON::toJsonTree).map(Object::toString).toList();
            assertThat(messages(snapshot, lang)).isEqualTo(expected);
            for (String body : expected) assertThat(body).contains("timestamp", "footer");
        }
        assertThat(snapshot.fingerprint()).isEqualTo(QuakeInfoSnapshot.sha256(snapshot.body()));
        assertThat(root.get("reportType").getAsString()).isEqualTo(type.name());
        assertThat(root.get("infoType").getAsString()).isEqualTo("発表");
        assertThat(root.get("serial").getAsInt()).isEqualTo(1);
        if (type == JMAXmlType.VXSE51 || type == JMAXmlType.VXSE53)
            assertThat(messages(snapshot, "ja_jp").getFirst()).contains("https://renderer.example.test");
    }

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE51", "VXSE52", "VXSE53", "VXSE61"})
    void rendersCorrectionsAndBodylessCancellations(JMAXmlType type) throws Exception {
        for (JMAInfoType infoType : List.of(JMAInfoType.訂正, JMAInfoType.取消)) {
            var quake = (QuakeInfo) report(type, 2, infoType, SeismicIntensity.THREE, START.plusSeconds(1600));
            var snapshot = QuakeInfoSnapshot.render(quake, context(new QuakeInfoStore()));
            var root = root(snapshot);
            assertThat(root.get("infoType").getAsString()).isEqualTo(infoType.name());
            assertThat(root.has("isCancel")).isFalse();
            assertThat(root.has("infoTypePriority")).isFalse();
            assertThat(root.getAsJsonObject("languages").keySet()).hasSize(5);
            if (infoType == JMAInfoType.取消) assertThat(messages(snapshot, "ja_jp").getFirst()).contains("取り消");
        }
    }

    @Test
    void retainsMultipleMessageBoundariesAndAbortsIfAnyLanguageFails() throws Exception {
        var quake = spy((QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1600)));
        var huge = PendingEmbed.empty().withDescription("分割テスト\n".repeat(2000));
        doReturn(List.of(huge)).when(quake).createEmbeds(anyString(), any(), any());
        var snapshot = QuakeInfoSnapshot.render(quake, context(new QuakeInfoStore()));
        assertThat(messages(snapshot, "ja_jp").size()).isGreaterThan(1);
        StringBuilder text = new StringBuilder();
        for (String body : messages(snapshot, "ja_jp")) {
            for (var e : JsonParser.parseString(body).getAsJsonArray()) text.append(e.getAsJsonObject().get("description").getAsString());
        }
        assertThat(text.toString()).isEqualTo(huge.description());
        doThrow(new IllegalStateException("synthetic render error")).when(quake).createEmbeds(eq("ko_kp"), any(), any());
        assertThatThrownBy(() -> QuakeInfoSnapshot.render(quake, context(new QuakeInfoStore()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void referenceEnrichmentChangesRenderedContentWithoutChangingSelectedReport() throws Exception {
        var store = new QuakeInfoStore();
        var latest = (QuakeInfo) report(JMAXmlType.VXSE52, 1, JMAInfoType.発表, null, START.plusSeconds(1600));
        store.putReport(latest);
        var before = QuakeInfoSnapshot.render(latest, context(store));
        store.putReport((QuakeInfo) report(JMAXmlType.VXSE51, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1500)));
        var after = QuakeInfoSnapshot.render(latest, context(store));
        assertThat(store.getLatestReport()).containsSame(latest);
        var beforeKey = root(before); beforeKey.remove("languages");
        var afterKey = root(after); afterKey.remove("languages");
        assertThat(afterKey).isEqualTo(beforeKey);
        assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
    }

    @Test
    void retainsExplicitNullDatesAndInvalidSerialSentinelInMetadata() throws Exception {
        var report = spy(report(JMAXmlType.VXSE53,1,JMAInfoType.発表,SeismicIntensity.FOUR,START.plusSeconds(1600)));
        doReturn(null).when(report).getReportDateTime();
        doReturn(null).when(report).getDateTime();
        doReturn("not-an-integer").when(report).getSerial();
        var snapshot = QuakeInfoSnapshot.render((QuakeInfo) report,context(new QuakeInfoStore()));
        var metadata = root(snapshot);
        assertThat(metadata.has("reportDateTime")).isTrue();
        assertThat(metadata.get("reportDateTime").isJsonNull()).isTrue();
        assertThat(metadata.get("dateTime").isJsonNull()).isTrue();
        assertThat(metadata.get("serial").getAsInt()).isEqualTo(-1);
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
