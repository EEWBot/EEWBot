package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.I18nEmbedBuilder;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.quakeinfo.QuakeInfoSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static net.teamfruit.eewbot.testutil.QuakeReports.*;
import static org.assertj.core.api.Assertions.assertThat;

class QuakeInfoReferenceTest {

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE52", "VXSE61"})
    void missingStoreAndCancellationOnlyReferenceAreSafeForBothOutputs(JMAXmlType type) throws Exception {
        var update = report(type, 1, JMAInfoType.発表, null, START.plusSeconds(1600));
        assertThat(((QuakeInfo) update).getQuakeInfoMaxInt(null)).isEmpty();
        var store = new QuakeInfoStore();
        JMAXmlType source = type == JMAXmlType.VXSE61 ? JMAXmlType.VXSE53 : JMAXmlType.VXSE51;
        store.putReport((QuakeInfo) report(source, 2, JMAInfoType.取消, null, START.plusSeconds(1500)));
        var context = new EmbedContext(new RendererQueryFactory(null, null), store, new I18n("ja_jp"));
        assertThat(update.createWebhooks("ja_jp", context)).hasSize(1);
        assertThat(((QuakeInfo) update).getQuakeInfoMaxInt(store)).isEmpty();
        var dto = (QuakeInfoExternalData) ((ExternalData) update).toExternalDto();
        assertThat(dto.getHypocenterName()).isNotBlank();
        assertThat(dto.getOriginTime()).isNotNull();
    }

    /** The VXSE52 map image draws the intensity areas of the event's VXSE51, looked up through the store. */
    @Test
    void epicenterImageCarriesTheReferencedIntensityAreas() throws Exception {
        var renderer = new RendererQueryFactory("http://renderer.test", "test-key");
        var update = report(JMAXmlType.VXSE52, 1, JMAInfoType.発表, null, START.plusSeconds(1600));

        String without = imageUrlHash(update, new EmbedContext(renderer, new QuakeInfoStore(), new I18n("ja_jp")));
        var store = new QuakeInfoStore();
        store.putReport((QuakeInfo) report(JMAXmlType.VXSE51, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1500)));
        String with = imageUrlHash(update, new EmbedContext(renderer, store, new I18n("ja_jp")));

        assertThat(without).isEqualTo(IMAGE_WITHOUT_REFERENCE);
        assertThat(with).isEqualTo(IMAGE_WITH_REFERENCE);
    }

    private static final String IMAGE_WITHOUT_REFERENCE = "61a81cf38db591b25f48b7953c30bfa7ad6a0ae2a37d93b217ce9ed646a9d8be";
    private static final String IMAGE_WITH_REFERENCE = "b4d86a25cb0673a0a2a1d1b1fc631a66a662aec32b91b6e13ca0108f8ca2c349";

    /** SHA-256 of the image URL: the URL itself is Base32768 text, awkward to keep in source. */
    private static String imageUrlHash(AbstractJMAReport report, EmbedContext context) {
        String url = report.createEmbeds("ja_jp", context, () -> new I18nEmbedBuilder("ja_jp", context.i18n()))
                .getFirst().image();
        assertThat(url).startsWith("http://renderer.test/");
        return QuakeInfoSnapshot.sha256(url);
    }
}
