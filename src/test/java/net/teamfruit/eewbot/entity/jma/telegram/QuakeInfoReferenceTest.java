package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.jma.QuakeInfo;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static net.teamfruit.eewbot.testutil.QuakeReports.*;
import static org.assertj.core.api.Assertions.assertThat;

class QuakeInfoReferenceTest {

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE52", "VXSE61"})
    void missingStoreAndCancellationOnlyReferenceAreSafeForBothOutputs(JMAXmlType type) throws Exception {
        var update = report(type, 1, JMAInfoType.発表, null, START.plusSeconds(1600));
        assertThat(((QuakeInfo) update).getQuakeInfoMaxInt()).isEmpty();
        var store = new QuakeInfoStore();
        JMAXmlType source = type == JMAXmlType.VXSE61 ? JMAXmlType.VXSE53 : JMAXmlType.VXSE51;
        store.putReport((QuakeInfo) report(source, 2, JMAInfoType.取消, null, START.plusSeconds(1500)));
        var context = new EmbedContext(new RendererQueryFactory(null, null), store, new I18n("ja_jp"));
        assertThat(update.createWebhooks("ja_jp", context)).hasSize(1);
        assertThat(((QuakeInfo) update).getQuakeInfoMaxInt()).isEmpty();
        var dto = (QuakeInfoExternalData) ((ExternalData) update).toExternalDto();
        assertThat(dto.getHypocenterName()).isNotBlank();
        assertThat(dto.getOriginTime()).isNotNull();
    }
}
