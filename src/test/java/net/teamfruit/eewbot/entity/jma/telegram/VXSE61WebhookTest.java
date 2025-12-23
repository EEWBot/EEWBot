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
import net.teamfruit.eewbot.registry.channel.SeismicIntensityDeserializer;
import net.teamfruit.eewbot.registry.channel.SeismicIntensitySerializer;
import net.teamfruit.eewbot.testutil.JsonAssertTestHelper;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VXSE61WebhookTest {

    private static final String TELEGRAM_TYPE = "vxse61";
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

        xmlMapper = XmlMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
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

    private void generateExpectedJsonFiles(String caseName, boolean overwrite) throws IOException {
        String xmlPath = "jmaxml/" + TELEGRAM_TYPE + "/" + caseName + ".xml";
        String baseOutputPath = "src/test/resources/jmaxml/" + TELEGRAM_TYPE + "/" + caseName;

        Path discordPath = Paths.get(baseOutputPath + "_discord_expected.json");
        Path externalPath = Paths.get(baseOutputPath + "_external_expected.json");

        // 上書き保護チェック
        if (!overwrite && Files.exists(discordPath) && Files.exists(externalPath)) {
            System.out.printf("[%s] スキップ: 期待値JSONファイルが既に存在します%n", caseName);
            System.out.println("  - Discord: " + discordPath);
            System.out.println("  - External: " + externalPath);
            return;
        }

        // XMLを読み込み
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();

        // XMLの生データも保持（改行コードをLFに統一）
        String rawXml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\R", "\n");
        xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);

        VXSE61Impl report = xmlMapper.readValue(xmlStream, VXSE61Impl.class);
        assertThat(report).isNotNull();

        // 1. Discord Webhook用JSON生成
        if (!overwrite && Files.exists(discordPath)) {
            System.out.printf("[%s] Discord Webhook JSON: 既に存在（スキップ）%n", caseName);
        } else {
            DiscordWebhook webhook = report.createWebhook("ja_jp");
            String discordJson = gson.toJson(webhook);
            Files.writeString(discordPath, discordJson, StandardCharsets.UTF_8);
            System.out.printf("[%s] Discord Webhook JSON生成: %s%n", caseName, discordPath);
        }

        // 2. ExternalWebhook用JSON生成
        if (!overwrite && Files.exists(externalPath)) {
            System.out.printf("[%s] External Webhook JSON: 既に存在（スキップ）%n", caseName);
        } else {
            report.setRawData(rawXml);
            Object externalDto = report.toExternalDto();
            ExternalWebhookRequest request = new ExternalWebhookRequest(
                    report.getDataType(),
                    1234567890000L,  // 固定値
                    report.getRawData(),
                    externalDto
            );
            String externalJson = gson.toJson(request);
            Files.writeString(externalPath, externalJson, StandardCharsets.UTF_8);
            System.out.printf("[%s] External Webhook JSON生成: %s%n", caseName, externalPath);
        }
    }

    /**
     * すべてのXMLファイルに対して、欠落している期待値JSONファイルを生成
     * 既存のJSONファイルは上書きしない（冪等性を保証）
     */
    @Test
    @Disabled("必要に応じて手動実行してください。実行後は再度@Disabledを付けることを推奨します。")
    void generateAllMissingExpectedJsonFiles() throws IOException {
        System.out.println("========================================");
        System.out.println("期待値JSON生成処理を開始します");
        System.out.println("テレグラムタイプ: " + TELEGRAM_TYPE);
        System.out.println("========================================");

        List<String> xmlFileNames = provideXmlFileNames().collect(Collectors.toList());

        int totalCount = xmlFileNames.size();
        List<String> generatedCases = new ArrayList<>();
        List<String> skippedCases = new ArrayList<>();
        List<String> errorCases = new ArrayList<>();

        for (String caseName : xmlFileNames) {
            try {
                Path discordPath = Paths.get("src/test/resources/jmaxml/" + TELEGRAM_TYPE + "/"
                        + caseName + "_discord_expected.json");
                Path externalPath = Paths.get("src/test/resources/jmaxml/" + TELEGRAM_TYPE + "/"
                        + caseName + "_external_expected.json");

                boolean discordExists = Files.exists(discordPath);
                boolean externalExists = Files.exists(externalPath);

                if (discordExists && externalExists) {
                    skippedCases.add(caseName);
                } else {
                    generateExpectedJsonFiles(caseName, false);
                    generatedCases.add(caseName);
                }
            } catch (Exception e) {
                errorCases.add(caseName);
                System.err.printf("[%s] エラー: %s%n", caseName, e.getMessage());
                e.printStackTrace();
            }
        }

        // サマリー出力
        System.out.println("========================================");
        System.out.println("期待値JSON生成処理が完了しました");
        System.out.println("----------------------------------------");
        System.out.println("総XMLファイル数: " + totalCount);
        System.out.println("新規生成: " + generatedCases.size() + "件 "
                + (generatedCases.isEmpty() ? "" : generatedCases));
        System.out.println("スキップ: " + skippedCases.size() + "件 "
                + (skippedCases.isEmpty() ? "" : skippedCases));
        System.out.println("エラー: " + errorCases.size() + "件 "
                + (errorCases.isEmpty() ? "" : errorCases));
        System.out.println("========================================");
    }

    @ParameterizedTest(name = "{0} - Discord Webhook JSON")
    @MethodSource("provideXmlFileNames")
    void testDiscordWebhookJson(String baseName) throws IOException, JSONException {
        String xmlPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + ".xml";
        String expectedJsonPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + "_discord_expected.json";
        testXmlToDiscordWebhook(xmlPath, expectedJsonPath, baseName);
    }

    @ParameterizedTest(name = "{0} - External Webhook JSON")
    @MethodSource("provideXmlFileNames")
    void testExternalWebhookJson(String baseName) throws IOException, JSONException {
        String xmlPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + ".xml";
        String expectedJsonPath = "jmaxml/" + TELEGRAM_TYPE + "/" + baseName + "_external_expected.json";
        testXmlToExternalWebhook(xmlPath, expectedJsonPath, baseName);
    }

    private void testXmlToDiscordWebhook(String xmlPath, String expectedJsonPath, String baseName) throws IOException, JSONException {
        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();
        VXSE61Impl report = xmlMapper.readValue(xmlStream, VXSE61Impl.class);
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
        JsonAssertTestHelper.assertJsonWithDump(
                expectedJson,
                actualJson,
                JSONCompareMode.NON_EXTENSIBLE,
                this.getClass().getSimpleName(),
                "testDiscordWebhookJson",
                baseName
        );
    }

    private void testXmlToExternalWebhook(String xmlPath, String expectedJsonPath, String baseName) throws IOException, JSONException {
        // 1. XMLを読み込んでデシリアライズ
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();

        // XMLの生データも保持（改行コードをLFに統一）
        String rawXml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\R", "\n");
        xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);

        VXSE61Impl report = xmlMapper.readValue(xmlStream, VXSE61Impl.class);
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
