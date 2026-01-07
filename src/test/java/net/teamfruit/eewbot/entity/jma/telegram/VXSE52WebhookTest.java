package net.teamfruit.eewbot.entity.jma.telegram;

import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

class VXSE52WebhookTest extends BaseWebhookTest<VXSE52Impl> {

    @Override
    protected String getTelegramType() {
        return "vxse52";
    }

    @Override
    protected Class<VXSE52Impl> getImplClass() {
        return VXSE52Impl.class;
    }

    /**
     * XMLファイルのリストを提供
     */
    private static Stream<String> provideXmlFileNames() throws IOException {
        return getXmlFileNames("vxse52");
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
