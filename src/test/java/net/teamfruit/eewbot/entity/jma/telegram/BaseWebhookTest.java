package net.teamfruit.eewbot.entity.jma.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.discord.DiscordWebhook;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.ExternalWebhookRequest;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.destination.model.SeismicIntensityDeserializer;
import net.teamfruit.eewbot.registry.destination.model.SeismicIntensitySerializer;
import net.teamfruit.eewbot.testutil.JsonAssertTestHelper;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VXSE*WebhookTestクラスの共通基底クラス
 * EEWBot初期化、XML/JSONマッパー、期待値JSON生成ロジックを共有
 */
public abstract class BaseWebhookTest<T extends AbstractJMAReport> {

    protected static ObjectMapper xmlMapper;
    protected static Gson gson;
    protected static I18n i18n;
    protected static EmbedContext embedContext;
    protected static String rendererHash;
    private static boolean initialized = false;

    /**
     * テレグラムタイプ（vxse51, vxse52等）を返す
     */
    protected abstract String getTelegramType();

    /**
     * 実装クラス（VXSE51Impl等）を返す
     */
    protected abstract Class<T> getImplClass();

    @BeforeAll
    static void setUpBase() {
        if (initialized) {
            return;
        }

        i18n = new I18n("ja_jp");
        String rendererAddress = System.getenv("EEWBOT_RENDERER_ADDRESS");
        String rendererKey = System.getenv("EEWBOT_RENDERER_KEY");
        RendererQueryFactory renderer = new RendererQueryFactory(rendererAddress, rendererKey);
        QuakeInfoStore store = new QuakeInfoStore();
        embedContext = new EmbedContext(renderer, store, i18n);
        rendererHash = computeRendererHash(rendererAddress, rendererKey);

        xmlMapper = XmlMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
                .create();

        initialized = true;
    }

    @BeforeEach
    void logTestStart(TestInfo testInfo) {
        String testName = testInfo.getDisplayName();
        String className = this.getClass().getSimpleName();
        System.out.printf("[TEST] %s: %s%n", className, testName);
    }

    /**
     * renderer設定のハッシュを算出（SHA-256先頭8文字hex）
     * 未設定時はnullを返す
     */
    static String computeRendererHash(String address, String key) {
        if (StringUtils.isEmpty(address) || StringUtils.isEmpty(key)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((address + ":" + key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * XMLファイルのリストを提供（@MethodSourceから呼ばれる）
     */
    protected static Stream<String> getXmlFileNames(String telegramType) throws IOException {
        Path testResourcePath = Paths.get("src/test/resources/jmaxml/" + telegramType);
        if (!Files.exists(testResourcePath)) {
            return Stream.empty();
        }
        try (Stream<Path> paths = Files.list(testResourcePath)) {
            return paths
                    .filter(p -> p.toString().endsWith(".xml"))
                    .map(p -> p.getFileName().toString().replace(".xml", ""))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    /**
     * Discord Webhook JSONテストを実行
     */
    protected void runDiscordWebhookTest(String baseName) throws IOException, JSONException {
        String xmlPath = "jmaxml/" + getTelegramType() + "/" + baseName + ".xml";
        String expectedJsonPath;
        if (rendererHash != null) {
            expectedJsonPath = "jmaxml/" + getTelegramType() + "/" + baseName + "_discord_expected_" + rendererHash + ".json";
        } else {
            expectedJsonPath = "jmaxml/" + getTelegramType() + "/" + baseName + "_discord_expected.json";
        }

        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();
        T report = xmlMapper.readValue(xmlStream, getImplClass());
        assertThat(report).isNotNull();

        // 2. Entity → DiscordWebhook変換
        DiscordWebhook webhook = report.createWebhook("ja_jp", embedContext);
        assertThat(webhook).isNotNull();

        // 3. Webhook → JSON変換
        String actualJson = gson.toJson(webhook);
        assertThat(actualJson).isNotEmpty();

        // 4. 期待値JSONを読み込み
        InputStream expectedStream = getClass().getClassLoader().getResourceAsStream(expectedJsonPath);
        assertThat(expectedStream).isNotNull();
        String expectedJson = new String(expectedStream.readAllBytes(), StandardCharsets.UTF_8);

        // 5. JSON比較（順番を無視）
        JsonAssertTestHelper.assertJsonWithDump(
                expectedJson,
                actualJson,
                JSONCompareMode.NON_EXTENSIBLE,
                this.getClass().getSimpleName(),
                "testDiscordWebhookJson",
                baseName
        );
    }

    /**
     * External Webhook JSONテストを実行
     */
    protected void runExternalWebhookTest(String baseName) throws IOException, JSONException {
        String xmlPath = "jmaxml/" + getTelegramType() + "/" + baseName + ".xml";
        String expectedJsonPath = "jmaxml/" + getTelegramType() + "/" + baseName + "_external_expected.json";

        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();

        // XMLの生データも保持（改行コードをLFに統一）
        String rawXml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\R", "\n");
        xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);

        T report = xmlMapper.readValue(xmlStream, getImplClass());
        assertThat(report).isNotNull();

        // rawDataをセット
        report.setRawData(rawXml);

        // 2. Entity → ExternalData変換
        ExternalData externalData = (ExternalData) report;
        Object externalDto = externalData.toExternalDto();
        assertThat(externalDto).isNotNull();

        // 3. ExternalWebhookRequest作成（期待値生成時と同じ固定timestamp）
        ExternalWebhookRequest request = new ExternalWebhookRequest(
                externalData.getDataType(),
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
        JsonAssertTestHelper.assertJsonWithDump(
                expectedJson,
                actualJson,
                JSONCompareMode.NON_EXTENSIBLE,
                this.getClass().getSimpleName(),
                "testExternalWebhookJson",
                baseName
        );
    }
}
