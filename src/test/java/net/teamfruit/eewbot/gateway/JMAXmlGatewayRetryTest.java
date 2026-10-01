package net.teamfruit.eewbot.gateway;

import com.sun.net.httpserver.HttpServer;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.jma.AbstractJMAReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JMAXmlGatewayRetryTest {

    private HttpServer server;
    private String root;
    private String reportXml;

    /**
     * 電文エンドポイントが叩かれた回数。
     */
    private final AtomicInteger reportHits = new AtomicInteger();
    /**
     * 電文エンドポイントが返すステータスコード。
     */
    private volatile int reportStatus = 200;
    /**
     * フィードの Last-Modified ヘッダーの値。
     */
    private volatile String feedLastModified = "Tue, 28 Jul 2026 16:41:00 GMT";
    /**
     * フィードエンドポイントが受け取った If-Modified-Since の値。
     */
    private final List<String> feedIfModifiedSince = new ArrayList<>();

    @BeforeEach
    public void setUp() throws IOException {
        this.reportXml = new String(Files.readAllBytes(Path.of("src/test/resources/jmaxml/vxse53/case1.xml")), StandardCharsets.UTF_8);

        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.root = "http://127.0.0.1:" + this.server.getAddress().getPort() + "/feed/";
        String reportUrl = "http://127.0.0.1:" + this.server.getAddress().getPort() + "/data/report.xml";

        this.server.createContext("/feed/eqvol.xml", exchange -> {
            String ims = exchange.getRequestHeaders().getFirst("If-Modified-Since");
            this.feedIfModifiedSince.add(ims);
            if (this.feedLastModified.equals(ims)) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            byte[] body = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                    + "<updated>2026-07-29T01:41:00+09:00</updated>"
                    + "<entry><title>震源・震度に関する情報</title>"
                    + "<id>" + reportUrl + "</id>"
                    + "<link type=\"application/xml\" href=\"" + reportUrl + "\"/>"
                    + "<updated>2026-07-29T01:41:00+09:00</updated>"
                    + "<author><name>気象庁</name></author>"
                    + "<content type=\"text\">test</content>"
                    + "</entry></feed>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Last-Modified", this.feedLastModified);
            exchange.sendResponseHeaders(200, body.length);
            write(exchange.getResponseBody(), body);
            exchange.close();
        });

        this.server.createContext("/data/report.xml", exchange -> {
            this.reportHits.incrementAndGet();
            if (this.reportStatus != 200) {
                exchange.sendResponseHeaders(this.reportStatus, -1);
                exchange.close();
                return;
            }
            byte[] body = this.reportXml.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            write(exchange.getResponseBody(), body);
            exchange.close();
        });

        this.server.start();
    }

    private static void write(OutputStream out, byte[] body) throws IOException {
        try (OutputStream os = out) {
            os.write(body);
        }
    }

    @AfterEach
    public void tearDown() {
        this.server.stop(0);
    }

    @Test
    public void failedReportIsRetriedOnTheNextPoll() {
        List<AbstractJMAReport> dispatched = new ArrayList<>();
        JMAXmlGateway gateway = new JMAXmlGateway(HttpClient.newHttpClient(), new QuakeInfoStore(), dispatched::add, this.root);

        // 1 回目のポーリング: lastIds/lastModified の初期化のみで、何も取得されない
        gateway.run();
        assertEquals(0, this.reportHits.get());
        assertEquals(0, dispatched.size());

        // フィードにエントリが増える(URL は同じ、Last-Modified は新しい)が、電文は 503 を返す
        this.feedLastModified = "Tue, 28 Jul 2026 16:44:00 GMT";
        this.reportStatus = 503;
        // ...さらに lastIds をリセットし、エントリが新規として扱われるようにする
        resetLastIds(gateway);

        gateway.run();
        assertEquals(1, this.reportHits.get());
        assertEquals(0, dispatched.size(), "失敗した電文はディスパッチされてはならない");

        // 2 回目のポーリング: フィードは変わっていないが、前回のポーリングが失敗しているので
        // その Last-Modified をキャッシュしていてはならず、200 が返ってエントリを再試行する
        this.reportStatus = 200;
        gateway.run();
        assertEquals(2, this.reportHits.get(), "失敗したエントリは再取得されなければならない");
        assertEquals(1, dispatched.size(), "再試行された電文はディスパッチされなければならない");

        // 3 回目のポーリング: すべて成功したので Last-Modified がキャッシュされ、304 が返る
        gateway.run();
        assertEquals(2, this.reportHits.get(), "正常に処理されたエントリは再取得されてはならない");
        assertEquals(1, dispatched.size());
        assertTrue(this.feedIfModifiedSince.contains(this.feedLastModified), "ポーリングが完全に成功したら If-Modified-Since が送信されなければならない");
    }

    private static void resetLastIds(JMAXmlGateway gateway) {
        try {
            java.lang.reflect.Field lastIds = JMAXmlGateway.class.getDeclaredField("lastIds");
            lastIds.setAccessible(true);
            lastIds.set(gateway, new ArrayList<String>());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
