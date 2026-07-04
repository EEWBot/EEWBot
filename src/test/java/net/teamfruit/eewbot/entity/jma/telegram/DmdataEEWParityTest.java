package net.teamfruit.eewbot.entity.jma.telegram;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEW;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.registry.destination.model.SeismicIntensityDeserializer;
import net.teamfruit.eewbot.registry.destination.model.SeismicIntensitySerializer;
import net.teamfruit.eewbot.testutil.JsonAssertTestHelper;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dmdata JSON（eew-information → DmdataEEW）とJMA XML（VXSE45/VXSE43 → EEW）の出力同一性を保証するパリティテスト。
 * 同一テレグラムのペアフィクスチャ（src/test/resources/dmdata/eew/{case}.json + {case}.xml）をパースし、
 * Discord Webhook（createWebhook）と外部Webhook DTO（toExternalDto）のシリアライズ結果の一致を検証する。
 * 生データ（rawData / ExternalWebhookRequestのdata）のみ比較対象外。
 */
class DmdataEEWParityTest {

    private static final String FIXTURE_DIR = "dmdata/eew/";
    private static final List<String> LANGUAGES = List.of("ja_jp", "en_us");

    /**
     * XML側の実装クラス。警報単独契約のフォールバック経路（VXSE43）を1ケースでカバーし、他はVXSE45。
     */
    private static final Map<String, Class<? extends AbstractEEW>> XML_IMPL_OVERRIDES = Map.of(
            "warning_regions", VXSE43Impl.class
    );

    private static Gson gson;
    private static EmbedContext embedContext;

    @BeforeAll
    static void setUp() {
        I18n i18n = new I18n("ja_jp");
        RendererQueryFactory renderer = new RendererQueryFactory(null, null);
        embedContext = new EmbedContext(renderer, new QuakeInfoStore(), i18n);

        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensitySerializer())
                .registerTypeAdapter(SeismicIntensity.class, new SeismicIntensityDeserializer())
                .create();
    }

    private static Stream<Arguments> provideCases() throws IOException {
        Path fixturePath = Paths.get("src/test/resources/" + FIXTURE_DIR);
        List<String> caseNames;
        try (Stream<Path> paths = Files.list(fixturePath)) {
            caseNames = paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .toList();
        }
        return caseNames.stream().flatMap(caseName -> Stream.of(
                Arguments.of(caseName, false),
                Arguments.of(caseName, true)));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = DmdataEEWParityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("fixture not found: %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static DmdataEEW parseJsonEntity(String caseName) throws IOException {
        DmdataEEW entity = Codecs.GSON.fromJson(readResource(FIXTURE_DIR + caseName + ".json"), DmdataEEW.class);
        assertThat(entity).isNotNull();
        assertThat(entity.getBody()).isNotNull();
        return entity;
    }

    private static EEW parseXmlEntity(String caseName) throws IOException {
        Class<? extends AbstractEEW> implClass = XML_IMPL_OVERRIDES.getOrDefault(caseName, VXSE45Impl.class);
        EEW entity = Codecs.XML_MAPPER.readValue(readResource(FIXTURE_DIR + caseName + ".xml"), implClass);
        assertThat(entity).isNotNull();
        return entity;
    }

    @ParameterizedTest(name = "{0} (concurrent={1}) - Discord Webhook parity")
    @MethodSource("provideCases")
    void testDiscordWebhookParity(String caseName, boolean concurrent) throws IOException, JSONException {
        DmdataEEW jsonEntity = parseJsonEntity(caseName);
        EEW xmlEntity = parseXmlEntity(caseName);
        applyConcurrent(jsonEntity, xmlEntity, concurrent);

        for (String lang : LANGUAGES) {
            String expected = gson.toJson(jsonEntity.createWebhook(lang, embedContext));
            String actual = gson.toJson(xmlEntity.createWebhook(lang, embedContext));
            JsonAssertTestHelper.assertJsonWithDump(
                    expected,
                    actual,
                    JSONCompareMode.NON_EXTENSIBLE,
                    getClass().getSimpleName(),
                    "testDiscordWebhookParity_" + lang + (concurrent ? "_concurrent" : ""),
                    caseName
            );
        }
    }

    @ParameterizedTest(name = "{0} (concurrent={1}) - External DTO parity")
    @MethodSource("provideCases")
    void testExternalDtoParity(String caseName, boolean concurrent) throws IOException, JSONException {
        DmdataEEW jsonEntity = parseJsonEntity(caseName);
        EEW xmlEntity = parseXmlEntity(caseName);
        applyConcurrent(jsonEntity, xmlEntity, concurrent);

        assertThat(xmlEntity.getDataType()).isEqualTo(jsonEntity.getDataType());

        String expected = gson.toJson(jsonEntity.toExternalDto());
        String actual = gson.toJson(xmlEntity.toExternalDto());
        JsonAssertTestHelper.assertJsonWithDump(
                expected,
                actual,
                JSONCompareMode.NON_EXTENSIBLE,
                getClass().getSimpleName(),
                "testExternalDtoParity" + (concurrent ? "_concurrent" : ""),
                caseName
        );
    }

    private static void applyConcurrent(DmdataEEW jsonEntity, EEW xmlEntity, boolean concurrent) {
        if (concurrent) {
            jsonEntity.setConcurrent(true);
            jsonEntity.setConcurrentIndex(2);
            xmlEntity.setConcurrent(true);
            xmlEntity.setConcurrentIndex(2);
        }
    }
}
