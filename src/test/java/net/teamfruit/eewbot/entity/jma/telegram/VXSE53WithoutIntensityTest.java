package net.teamfruit.eewbot.entity.jma.telegram;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.EmbedContext;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.renderer.RendererQueryFactory;
import net.teamfruit.eewbot.i18n.I18n;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VXSE53WithoutIntensityTest {

    static Stream<Arguments> reportsWithoutIntensity() {
        return Stream.of("遠地地震に関する情報", "震源・震度情報")
                .flatMap(title -> Stream.of("", "<Intensity/>", "<Intensity><Observation/></Intensity>")
                        .map(intensity -> Arguments.of(title, intensity)));
    }

    @ParameterizedTest
    @MethodSource("reportsWithoutIntensity")
    void reportWithoutIntensityCanCreateBothDiscordOutputsAndExternalDto(String title, String intensity) throws Exception {
        String xml = Files.readString(Path.of("src/test/resources/jmaxml/quakeinfo/overseas_without_intensity.xml"))
                .replace("<Title>遠地地震に関する情報</Title>", "<Title>" + title + "</Title>")
                .replace("<Comments>", intensity + "<Comments>");
        VXSE53Impl report = Codecs.XML_MAPPER.readValue(xml, VXSE53Impl.class);
        var context = new EmbedContext(new RendererQueryFactory("https://renderer.example/", "test"),
                new QuakeInfoStore(), new I18n("ja_jp"));
        assertThat(report.getQuakeInfoMaxInt()).isEmpty();
        assertThat(report.createMessages("ja_jp", context)).hasSize(1);
        assertThat(report.createWebhooks("ja_jp", context)).hasSize(1);
        var dto = (QuakeInfoExternalData) report.toExternalDto();
        assertThat(dto.getMaxInt()).isNull();
        assertThat(dto.getIntensities()).isNullOrEmpty();
        assertThat(dto.getHypocenterName()).isEqualTo("南太平洋");
        assertThat(dto.getMagnitude()).isEqualTo("7.0");
        assertThat(dto.getOriginTime()).isNotNull();
    }
}
