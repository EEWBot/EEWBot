package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.external.ExternalWebhookRequest;
import net.teamfruit.eewbot.registry.channel.SeismicIntensitySerializer;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VXSE53WebhookTest {

    private static ObjectMapper xmlMapper;
    private static Gson gson;

    @BeforeAll
    static void setUp() {
        // XML Mapperの初期化
        xmlMapper = XmlMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        // GSONの初期化（EEWBot.GSONと同じ設定）
        gson = new GsonBuilder()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .create();
    }

    @Test
    void testDiscordWebhookJson_case1() throws IOException, JSONException {
        testXmlToDiscordWebhook("jmaxml/vxse53/case1.xml", "jmaxml/vxse53/case1_discord_expected.json");
    }

    @Test
    void testDiscordWebhookJson_case2() throws IOException, JSONException {
        testXmlToDiscordWebhook("jmaxml/vxse53/case2.xml", "jmaxml/vxse53/case2_discord_expected.json");
    }

    @Test
    void testExternalWebhookJson_case1() throws IOException, JSONException {
        testXmlToExternalWebhook("jmaxml/vxse53/case1.xml", "jmaxml/vxse53/case1_external_expected.json");
    }

    @Test
    void testExternalWebhookJson_case2() throws IOException, JSONException {
        testXmlToExternalWebhook("jmaxml/vxse53/case2.xml", "jmaxml/vxse53/case2_external_expected.json");
    }

    private void testXmlToDiscordWebhook(String xmlPath, String expectedJsonPath) throws IOException, JSONException {
        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();
        VXSE53Impl report = xmlMapper.readValue(xmlStream, VXSE53Impl.class);
        assertThat(report).isNotNull();

        // 2. Entity → DiscordWebhook変換
        DiscordWebhook webhook = report.createWebhook("ja_JP");
        assertThat(webhook).isNotNull();

        // 3. Webhook → JSON変換
        String actualJson = gson.toJson(webhook);
        assertThat(actualJson).isNotEmpty();

        // 4. 期待値JSONを読み込み
        InputStream expectedStream = getClass().getClassLoader().getResourceAsStream(expectedJsonPath);
        assertThat(expectedStream).isNotNull();
        String expectedJson = new String(expectedStream.readAllBytes(), StandardCharsets.UTF_8);

        // 5. JSON比較（厳密モード）
        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT);
    }

    private void testXmlToExternalWebhook(String xmlPath, String expectedJsonPath) throws IOException, JSONException {
        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();

        // XMLの生データも保持
        String rawXml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8);
        xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);

        VXSE53Impl report = xmlMapper.readValue(xmlStream, VXSE53Impl.class);
        assertThat(report).isNotNull();

        // rawDataをセット
        report.setRawData(rawXml);

        // 2. Entity → ExternalData変換
        Object externalDto = report.toExternalDto();
        assertThat(externalDto).isNotNull();

        // 3. ExternalWebhookRequest作成
        ExternalWebhookRequest request = new ExternalWebhookRequest(
                report.getDataType(),
                Instant.now().toEpochMilli(),
                report.getRawData(),
                externalDto
        );

        // 4. ExternalWebhookRequest → JSON変換
        String actualJson = gson.toJson(request);
        assertThat(actualJson).isNotEmpty();

        // 5. 期待値JSONを読み込み
        InputStream expectedStream = getClass().getClassLoader().getResourceAsStream(expectedJsonPath);
        assertThat(expectedStream).isNotNull();
        String expectedJson = new String(expectedStream.readAllBytes(), StandardCharsets.UTF_8);

        // 6. JSON比較（LENIENTモード - timestampが変動するため）
        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.LENIENT);
    }
}
