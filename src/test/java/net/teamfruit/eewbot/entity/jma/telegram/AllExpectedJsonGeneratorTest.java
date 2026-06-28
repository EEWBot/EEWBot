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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("CallToPrintStackTrace")
class AllExpectedJsonGeneratorTest {

    private static ObjectMapper xmlMapper;
    private static Gson gson;
    private static I18n i18n;
    private static EmbedContext embedContext;
    private static EmbedContext embedContextWithRenderer;
    private static String rendererHash;

    // テレグラムタイプと実装クラスのマッピング
    private static final Map<String, Class<? extends AbstractJMAReport>> TELEGRAM_TYPES = new LinkedHashMap<>();

    static {
        TELEGRAM_TYPES.put("vxse51", VXSE51Impl.class);
        TELEGRAM_TYPES.put("vxse52", VXSE52Impl.class);
        TELEGRAM_TYPES.put("vxse53", VXSE53Impl.class);
        TELEGRAM_TYPES.put("vxse61", VXSE61Impl.class);
        TELEGRAM_TYPES.put("vtse41", VTSE41Impl.class);
    }

    @BeforeAll
    static void setUp() {
        i18n = new I18n("ja_jp");
        QuakeInfoStore store = new QuakeInfoStore();

        RendererQueryFactory rendererDisabled = new RendererQueryFactory(null, null);
        embedContext = new EmbedContext(rendererDisabled, store, i18n);

        String rendererAddress = System.getenv("EEWBOT_RENDERER_ADDRESS");
        String rendererKey = System.getenv("EEWBOT_RENDERER_KEY");
        rendererHash = BaseWebhookTest.computeRendererHash(rendererAddress, rendererKey);
        if (rendererHash != null) {
            RendererQueryFactory rendererEnabled = new RendererQueryFactory(rendererAddress, rendererKey);
            embedContextWithRenderer = new EmbedContext(rendererEnabled, store, i18n);
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
     * すべてのテレグラムタイプのXMLファイルに対して、期待値JSONファイルを生成（既存ファイルは上書き）
     * 実行方法: ./gradlew updateGolden
     */
    @Test
    @EnabledIfSystemProperty(named = "update-golden", matches = "true")
    void generateAllExpectedJsonFiles() throws IOException {
        System.out.println("========================================");
        System.out.println("全テレグラムタイプの期待値JSON生成処理を開始します");
        System.out.println("========================================");

        int totalTypes = TELEGRAM_TYPES.size();
        int currentType = 0;

        for (Map.Entry<String, Class<? extends AbstractJMAReport>> entry : TELEGRAM_TYPES.entrySet()) {
            currentType++;
            String telegramType = entry.getKey();
            Class<? extends AbstractJMAReport> implClass = entry.getValue();

            System.out.println();
            System.out.printf("[%d/%d] テレグラムタイプ: %s%n", currentType, totalTypes, telegramType);
            System.out.println("----------------------------------------");

            generateForType(telegramType, implClass);
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("全テレグラムタイプの期待値JSON生成処理が完了しました");
        System.out.println("========================================");
    }

    private void generateForType(String telegramType, Class<? extends AbstractJMAReport> implClass) throws IOException {
        List<String> xmlFileNames = getXmlFileNames(telegramType).toList();

        int totalCount = xmlFileNames.size();
        List<String> generatedCases = new ArrayList<>();
        List<String> errorCases = new ArrayList<>();

        for (String caseName : xmlFileNames) {
            try {
                generateExpectedJsonFiles(telegramType, implClass, caseName);
                generatedCases.add(caseName);
            } catch (Exception e) {
                errorCases.add(caseName);
                System.err.printf("[%s] エラー: %s%n", caseName, e.getMessage());
                e.printStackTrace();
            }
        }

        // サマリー出力
        System.out.println("総XMLファイル数: " + totalCount);
        System.out.println("生成: " + generatedCases.size() + "件 "
                + (generatedCases.isEmpty() ? "" : generatedCases));
        System.out.println("エラー: " + errorCases.size() + "件 "
                + (errorCases.isEmpty() ? "" : errorCases));
    }

    private static Stream<String> getXmlFileNames(String telegramType) throws IOException {
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

    private void generateExpectedJsonFiles(String telegramType, Class<? extends AbstractJMAReport> implClass,
                                           String caseName) throws IOException {
        String xmlPath = "jmaxml/" + telegramType + "/" + caseName + ".xml";
        String baseOutputPath = "src/test/resources/jmaxml/" + telegramType + "/" + caseName;

        Path discordPath = Paths.get(baseOutputPath + "_discord_expected.json");
        Path externalPath = Paths.get(baseOutputPath + "_external_expected.json");

        // XMLを読み込み
        InputStream xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);
        assertThat(xmlStream).isNotNull();

        // XMLの生データも保持（改行コードをLFに統一）
        String rawXml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\R", "\n");
        xmlStream = getClass().getClassLoader().getResourceAsStream(xmlPath);

        AbstractJMAReport report = xmlMapper.readValue(xmlStream, implClass);
        assertThat(report).isNotNull();

        // 1. Discord Webhook用JSON生成（rendererなし）
        DiscordWebhook webhook = report.createWebhook("ja_jp", embedContext);
        String discordJson = gson.toJson(webhook).replaceAll("\\R", "\n");
        Files.writeString(discordPath, discordJson, StandardCharsets.UTF_8);
        System.out.printf("[%s] Discord Webhook JSON生成: %s%n", caseName, discordPath);

        // 1b. Discord Webhook用JSON生成（renderer有効、環境変数設定時のみ）
        if (embedContextWithRenderer != null) {
            // renderer有効版はXMLを再読み込みして生成
            InputStream xmlStreamForRenderer = getClass().getClassLoader().getResourceAsStream(xmlPath);
            assertThat(xmlStreamForRenderer).isNotNull();
            AbstractJMAReport reportForRenderer = xmlMapper.readValue(xmlStreamForRenderer, implClass);

            DiscordWebhook webhookWithRenderer = reportForRenderer.createWebhook("ja_jp", embedContextWithRenderer);
            String discordJsonWithRenderer = gson.toJson(webhookWithRenderer).replaceAll("\\R", "\n");
            Path discordRendererPath = Paths.get(baseOutputPath + "_discord_expected_" + rendererHash + ".json");
            Files.writeString(discordRendererPath, discordJsonWithRenderer, StandardCharsets.UTF_8);
            System.out.printf("[%s] Discord Webhook JSON生成 (renderer): %s%n", caseName, discordRendererPath);
        }

        // 2. ExternalWebhook用JSON生成
        report.setRawData(rawXml);
        ExternalData externalData = (ExternalData) report;
        Object externalDto = externalData.toExternalDto();
        ExternalWebhookRequest request = new ExternalWebhookRequest(
                externalData.getDataType(),
                1234567890000L,  // 固定値
                report.getRawData(),
                externalDto
        );
        String externalJson = gson.toJson(request).replaceAll("\\R", "\n");
        Files.writeString(externalPath, externalJson, StandardCharsets.UTF_8);
        System.out.printf("[%s] External Webhook JSON生成: %s%n", caseName, externalPath);
    }
}
