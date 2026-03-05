package net.teamfruit.eewbot.entity.jma.telegram;

import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

class VTSE41WebhookTest extends BaseWebhookTest<VTSE41Impl> {

    @Override
    protected String getTelegramType() {
        return "vtse41";
    }

    @Override
    protected Class<VTSE41Impl> getImplClass() {
        return VTSE41Impl.class;
    }

    /**
     * XMLファイルのリストを提供
     */
    private static Stream<String> provideXmlFileNames() throws IOException {
        return getXmlFileNames("vtse41");
    }

    @ParameterizedTest(name = "{0} - Discord Webhook JSON")
    @MethodSource("provideXmlFileNames")
    void testDiscordWebhookJson(String baseName) throws IOException, JSONException {
        runDiscordWebhookTest(baseName);
    }

    @ParameterizedTest(name = "{0} - External Webhook JSON")
    @MethodSource("provideXmlFileNames")
    void testExternalWebhookJson(String baseName) throws IOException, JSONException {
        runExternalWebhookTest(baseName);
    }
}
