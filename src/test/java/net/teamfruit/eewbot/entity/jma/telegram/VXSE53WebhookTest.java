package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.external.ExternalWebhookRequest;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.channel.SeismicIntensitySerializer;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VXSE53WebhookTest {

    private static final String TELEGRAM_TYPE = "vxse53";
    private static ObjectMapper xmlMapper;
    private static Gson gson;

    @BeforeAll
    static void setUp() {
        // EEWBotインスタンスを作成してI18nを初期化
        EEWBot eewBot = new EEWBot();
        EEWBot.instance = eewBot;
        try {
            java.lang.reflect.Field i18nField = EEWBot.class.getDeclaredField("i18n");
            i18nField.setAccessible(true);
            i18nField.set(eewBot, new I18n("ja_jp"));

            // RendererQueryFactoryを初期化（nullでOK - isAvailable()がfalseを返す）
            java.lang.reflect.Field rendererField = EEWBot.class.getDeclaredField("rendererQueryFactory");
            rendererField.setAccessible(true);
            rendererField.set(eewBot, new RendererQueryFactory(null, null));

            // QuakeInfoStoreを初期化（空のストアでOK）
            java.lang.reflect.Field quakeInfoStoreField = EEWBot.class.getDeclaredField("quakeInfoStore");
            quakeInfoStoreField.setAccessible(true);
            quakeInfoStoreField.set(eewBot, new QuakeInfoStore());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize EEWBot for test", e);
        }

        // XML Mapperの初期化
        xmlMapper = XmlMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        // GSONの初期化（EEWBot.GSONと同じ設定）
        gson = new GsonBuilder()
                .setPrettyPrinting()  // 読みやすいように整形
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .create();
    }

    /**
     * XMLファイルのリストを提供
     */
    private static Stream<String> provideXmlFileNames() throws IOException {
        Path testResourcePath = Paths.get("src/test/resources/jmaxml/" + TELEGRAM_TYPE);
        if (!Files.exists(testResourcePath)) {
            return Stream.empty();
        }
        return Files.list(testResourcePath)
                .filter(p -> p.toString().endsWith(".xml"))
                .map(p -> p.getFileName().toString().replace(".xml", ""))
                .sorted();
    }

    /**
     * 期待値JSONを生成するヘルパーメソッド
     * 一度実行したら@Disabledを付けて無効化してください
     */
    @Test
    @Disabled("期待値JSONを生成したら無効化してください")
    void generateExpectedJson_case1() throws IOException {
        generateExpectedJsonFiles("case1");
    }

    private void generateExpectedJsonFiles(String caseName) throws IOException {
        String xmlPath = "jmaxml/vxse53/" + caseName + ".xml";
        String baseOutputPath = "src/test/resources/jmaxml/vxse53/" + caseName;

        // XMLを読み込み
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();

        // XMLの生データも保持（改行コードをLFに統一）
        String rawXml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\R", "\n");
        xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);

        VXSE53Impl report = xmlMapper.readValue(xmlStream, VXSE53Impl.class);
        assertThat(report).isNotNull();

        // 1. Discord Webhook用JSON生成
        DiscordWebhook webhook = report.createWebhook("ja_jp");
        String discordJson = gson.toJson(webhook);
        Path discordPath = Paths.get(baseOutputPath + "_discord_expected.json");
        Files.writeString(discordPath, discordJson, StandardCharsets.UTF_8);
        System.out.println("✓ Discord Webhook JSON生成: " + discordPath);

        // 2. ExternalWebhook用JSON生成
        report.setRawData(rawXml);
        Object externalDto = report.toExternalDto();
        ExternalWebhookRequest request = new ExternalWebhookRequest(
                report.getDataType(),
                1234567890000L,  // 固定値
                report.getRawData(),
                externalDto
        );
        String externalJson = gson.toJson(request);
        Path externalPath = Paths.get(baseOutputPath + "_external_expected.json");
        Files.writeString(externalPath, externalJson, StandardCharsets.UTF_8);
        System.out.println("✓ External Webhook JSON生成: " + externalPath);
    }

    @ParameterizedTest(name = "{0} - Discord Webhook JSON")
    @MethodSource("provideXmlFileNames")
    void testDiscordWebhookJson(String baseName) throws IOException, JSONException {
        String xmlPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + ".xml";
        String expectedJsonPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + "_discord_expected.json";
        testXmlToDiscordWebhook(xmlPath, expectedJsonPath);
    }

    @ParameterizedTest(name = "{0} - External Webhook JSON")
    @MethodSource("provideXmlFileNames")
    void testExternalWebhookJson(String baseName) throws IOException, JSONException {
        String xmlPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + ".xml";
        String expectedJsonPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + "_external_expected.json";
        testXmlToExternalWebhook(xmlPath, expectedJsonPath);
    }

    private void testXmlToDiscordWebhook(String xmlPath, String expectedJsonPath) throws IOException, JSONException {
        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();
        VXSE53Impl report = xmlMapper.readValue(xmlStream, VXSE53Impl.class);
        assertThat(report).isNotNull();

        // 2. Entity → DiscordWebhook変換
        DiscordWebhook webhook = report.createWebhook("ja_jp");
        assertThat(webhook).isNotNull();

        // 3. Webhook → JSON変換
        String actualJson = gson.toJson(webhook);
        assertThat(actualJson).isNotEmpty();

        // 4. 期待値JSONを読み込み
        InputStream expectedStream = getClass().getClassLoader().getResourceAsStream(expectedJsonPath);
        assertThat(expectedStream).isNotNull();
        String expectedJson = new String(expectedStream.readAllBytes(), StandardCharsets.UTF_8);

        // 5. JSON比較（順番を無視）
        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.NON_EXTENSIBLE);
    }

    private void testXmlToExternalWebhook(String xmlPath, String expectedJsonPath) throws IOException, JSONException {
        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();

        // XMLの生データも保持（改行コードをLFに統一）
        String rawXml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\R", "\n");
        xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);

        VXSE53Impl report = xmlMapper.readValue(xmlStream, VXSE53Impl.class);
        assertThat(report).isNotNull();

        // rawDataをセット
        report.setRawData(rawXml);

        // 2. Entity → ExternalData変換
        Object externalDto = report.toExternalDto();
        assertThat(externalDto).isNotNull();

        // 3. ExternalWebhookRequest作成（期待値生成時と同じ固定timestamp）
        ExternalWebhookRequest request = new ExternalWebhookRequest(
                report.getDataType(),
                1234567890000L,  // 固定値
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

        // 6. JSON比較（順番を無視）
        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.NON_EXTENSIBLE);
    }
}
