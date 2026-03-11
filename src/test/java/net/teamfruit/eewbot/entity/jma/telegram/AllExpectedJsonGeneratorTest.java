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
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.ExternalWebhookRequest;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.destination.model.SeismicIntensityDeserializer;
import net.teamfruit.eewbot.registry.destination.model.SeismicIntensitySerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
     * すべてのテレグラムタイプのXMLファイルに対して、欠落している期待値JSONファイルを生成
     * 既存のJSONファイルは上書きしない（冪等性を保証）
     */
    @Test
    @Disabled("必要に応じて手動実行してください。実行後は再度@Disabledを付けることを推奨します。")
    void generateAllMissingExpectedJsonFiles() throws IOException {
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
        List<String> skippedCases = new ArrayList<>();
        List<String> errorCases = new ArrayList<>();

        for (String caseName : xmlFileNames) {
            try {
                Path discordPath = Paths.get("src/test/resources/jmaxml/" + telegramType + "/"
                        + caseName + "_discord_expected.json");
                Path externalPath = Paths.get("src/test/resources/jmaxml/" + telegramType + "/"
                        + caseName + "_external_expected.json");

                boolean discordExists = Files.exists(discordPath);
                boolean externalExists = Files.exists(externalPath);

                if (discordExists && externalExists) {
                    skippedCases.add(caseName);
                } else {
                    generateExpectedJsonFiles(telegramType, implClass, caseName, true);
                    generatedCases.add(caseName);
                }
            } catch (Exception e) {
                errorCases.add(caseName);
                System.err.printf("[%s] エラー: %s%n", caseName, e.getMessage());
                e.printStackTrace();
            }
        }

        // サマリー出力
        System.out.println("総XMLファイル数: " + totalCount);
        System.out.println("新規生成: " + generatedCases.size() + "件 "
                + (generatedCases.isEmpty() ? "" : generatedCases));
        System.out.println("スキップ: " + skippedCases.size() + "件 "
                + (skippedCases.isEmpty() ? "" : skippedCases));
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
                                           String caseName, @SuppressWarnings("SameParameterValue") boolean overwrite) throws IOException {
        String xmlPath = "jmaxml/" + telegramType + "/" + caseName + ".xml";
        String baseOutputPath = "src/test/resources/jmaxml/" + telegramType + "/" + caseName;

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

        AbstractJMAReport report = xmlMapper.readValue(xmlStream, implClass);
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
            ExternalData externalData = (ExternalData) report;
            Object externalDto = externalData.toExternalDto();
            ExternalWebhookRequest request = new ExternalWebhookRequest(
                    externalData.getDataType(),
                    1234567890000L,  // 固定値
                    report.getRawData(),
                    externalDto
            );
            String externalJson = gson.toJson(request);
            Files.writeString(externalPath, externalJson, StandardCharsets.UTF_8);
            System.out.printf("[%s] External Webhook JSON生成: %s%n", caseName, externalPath);
        }
    }
}
