package net.teamfruit.eewbot.testutil;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAXmlType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class QuakeReports {

    public static final Instant START = Instant.parse("2025-12-08T14:00:00Z");
    public static final String EVENT = "20251208231519";

    private QuakeReports() {
    }

    public static AbstractJMAReport report(JMAXmlType type, int serial, JMAInfoType infoType,
                                          SeismicIntensity intensity, Instant time) throws IOException {
        String xml = Files.readString(Path.of("src/test/resources/jmaxml/" + type.name().toLowerCase(java.util.Locale.ROOT) + "/case1.xml"));
        xml = xml.replaceAll("<Serial(?:>.*?</Serial>|\\s*/>)", "<Serial>" + serial + "</Serial>")
                .replaceAll("<InfoType>.*?</InfoType>", "<InfoType>" + infoType + "</InfoType>")
                .replaceAll("<DateTime>.*?</DateTime>", "<DateTime>" + time + "</DateTime>")
                .replaceAll("<ReportDateTime>.*?</ReportDateTime>", "<ReportDateTime>" + time + "</ReportDateTime>");
        if (infoType == JMAInfoType.取消)
            xml = xml.replaceAll("(?s)<Body.*?</Body>", "<Body xmlns=\"http://xml.kishou.go.jp/jmaxml1/body/seismology1/\"><Text>取消</Text></Body>");
        else if (intensity != null)
            xml = xml.replaceAll("<MaxInt>.*?</MaxInt>", "<MaxInt>" + intensity.getSymbolIntensity() + "</MaxInt>");
        return Codecs.XML_MAPPER.readValue(xml, type.getReportClass());
    }
}
