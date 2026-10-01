package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JMAXmlGatewayInterruptTest {

    private static final String ROOT = "https://example.com/feed/";
    private static final URI INTERRUPTED_REPORT = URI.create(ROOT + "interrupted.xml");
    private static final URI LATER_REPORT = URI.create(ROOT + "later.xml");

    @Test
    void interruptedReportFetchStopsPollAndRestoresInterruptFlag() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        String knownEntry = entry(ROOT + "known.xml");
        // The gateway processes new entries in reverse feed order.
        String updatedFeed = feed(entry(LATER_REPORT.toString()) + entry(INTERRUPTED_REPORT.toString()) + knownEntry);
        String reportXml = Files.readString(Path.of("src/test/resources/jmaxml/vxse53/case1.xml"));
        HttpResponse<InputStream> initialFeedResponse = response(feed(knownEntry));
        HttpResponse<InputStream> updatedFeedResponse = response(updatedFeed);
        HttpResponse<InputStream> laterReportResponse = response(reportXml);
        when(httpClient.<InputStream>send(any(HttpRequest.class), any()))
                .thenReturn(initialFeedResponse)
                .thenReturn(updatedFeedResponse)
                .thenThrow(new InterruptedException("Report fetch interrupted"))
                .thenReturn(laterReportResponse);

        List<AbstractJMAReport> dispatched = new ArrayList<>();
        JMAXmlGateway gateway = new JMAXmlGateway(httpClient, new QuakeInfoStore(), dispatched::add, ROOT);
        String originalThreadName = Thread.currentThread().getName();
        try {
            gateway.run(); // Initialize the feed cache before new reports arrive.
            gateway.run();

            assertAll(
                    () -> assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag must be restored"),
                    () -> verify(httpClient).send(argThat(request -> request.uri().equals(INTERRUPTED_REPORT)), any()),
                    () -> verify(httpClient, never()).send(argThat(request -> request.uri().equals(LATER_REPORT)), any()),
                    () -> assertTrue(dispatched.isEmpty(), "No reports may be dispatched after interruption")
            );
        } finally {
            Thread.interrupted(); // Do not leave the JUnit worker interrupted.
            Thread.currentThread().setName(originalThreadName);
        }
    }

    private static String feed(String entries) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<updated>2026-07-29T01:41:00+09:00</updated>"
                + entries + "</feed>";
    }

    private static String entry(String url) {
        return "<entry><title>震源・震度に関する情報</title>"
                + "<id>" + url + "</id>"
                + "<link type=\"application/xml\" href=\"" + url + "\"/>"
                + "<updated>2026-07-29T01:41:00+09:00</updated>"
                + "<author><name>気象庁</name></author>"
                + "<content type=\"text\">test</content></entry>";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(String body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("Last-Modified", List.of("Tue, 28 Jul 2026 16:41:00 GMT")),
                (name, value) -> true
        ));
        return response;
    }
}
