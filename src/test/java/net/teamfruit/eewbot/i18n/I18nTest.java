package net.teamfruit.eewbot.i18n;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class I18nTest {

    private static final I18n I18N = new I18n("ja_jp");

    @ParameterizedTest
    @CsvSource({
            "en_us, eewbot.scmd.testmessage.nowebhook",
            "en_us, eewbot.scmd.testmessage.success.webhooksender",
            "ko_hanja, eewbot.scmd.setup.permserror.managewebhooks",
            "ko_hanja, eewbot.scmd.testmessage.success.webhooksender"
    })
    void localizedMessagesContainActualLineBreaks(String language, String key) {
        assertThat(I18N.get(language, key))
                .contains("\n")
                .doesNotContain("\\n");
    }
}
