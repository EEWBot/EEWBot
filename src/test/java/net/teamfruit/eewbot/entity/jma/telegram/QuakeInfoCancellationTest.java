package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static net.teamfruit.eewbot.testutil.QuakeReports.*;
import static org.assertj.core.api.Assertions.assertThat;

class QuakeInfoCancellationTest {

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE51", "VXSE52", "VXSE53", "VXSE61"})
    void cancellationStillCreatesDiscordEmbedWithoutHypocenterOrComments(JMAXmlType type) throws Exception {
        var cancel = report(type, 2, JMAInfoType.取消, null, START.plusSeconds(1800));
        var context = new EmbedContext(new RendererQueryFactory(null, null), new QuakeInfoStore(), new I18n("ja_jp"));
        assertThat(cancel.createWebhooks("ja_jp", context)).hasSize(1);
    }
}
