package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import net.teamfruit.eewbot.registry.destination.store.ChannelRegistrySql;
import net.teamfruit.eewbot.registry.destination.store.DatabaseInitializer;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteDestinationImportTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ID = RemoteApiFixture.ID;
    private static final String SECRET = RemoteApiFixture.SECRET;
    private static final String TOKEN = "synthetic-token-xxxxxxxxxxxx";
    private static final Function<String, String> ENV = Map.of(
            "EEWBOT_CF_ACCESS_CLIENT_ID", ID, "EEWBOT_CF_ACCESS_CLIENT_SECRET", SECRET)::get;

    @TempDir Path dir;
    private Path db;
    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final Queue<Integer> statuses = new ConcurrentLinkedQueue<>();
    // Worker stand-in: an upsert store that counts only changed rows, and serves it as the snapshot.
    private final Map<String, JsonNode> remote = Collections.synchronizedMap(new TreeMap<>());
    private volatile boolean badCredentials;

    @BeforeEach void setup() throws Exception {
        this.db = this.dir.resolve("channels.db");
        ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(this.db);
        DatabaseInitializer.migrate(registry.getDataSource(), SQLDialect.SQLITE);
        registry.close();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                if (!ID.equals(exchange.getRequestHeaders().getFirst("CF-Access-Client-Id"))
                        || !SECRET.equals(exchange.getRequestHeaders().getFirst("CF-Access-Client-Secret"))) {
                    this.badCredentials = true;
                }
                String reply;
                int status = 200;
                if (path.equals(RemoteMigrationApiClient.PATH) && exchange.getRequestMethod().equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    this.bodies.add(body);
                    Integer forced = this.statuses.poll();
                    if (forced != null) status = forced;
                    int affected = 0;
                    if (status == 200) {
                        for (JsonNode d : JSON.readTree(body).get("destinations")) {
                            if (!d.equals(this.remote.put(d.get("targetId").textValue(), d))) affected++;
                        }
                    }
                    reply = "{\"affected\":" + affected + "}";
                } else if (path.equals(RemoteApiFixture.SNAPSHOT)) {
                    ObjectNode root = JSON.createObjectNode().put("revision", 3);
                    ArrayNode array = root.putArray("destinations");
                    this.remote.values().forEach(array::add);
                    reply = root.toString();
                } else {
                    status = 404;
                    reply = "{}";
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        this.server.start();
    }

    @AfterEach void teardown() { this.server.stop(0); }

    private String origin() { return "http://127.0.0.1:" + this.server.getAddress().getPort(); }

    private void insert(long target, long channel, Long thread, Long guild, int flags, int intensity, String lang, String webhookUrl) {
        ChannelRegistrySql registry;
        try { registry = ChannelRegistrySql.forSQLite(this.db); } catch (Exception e) { throw new RuntimeException(e); }
        registry.getDsl().execute("INSERT INTO destinations (target_id, channel_id, thread_id, guild_id, eew_alert, eew_prediction,"
                        + " eew_decimation, quake_info, tsunami, min_intensity, lang, webhook_url) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                target, channel, thread, guild, flags & 1, flags >> 1 & 1, flags >> 2 & 1, flags >> 3 & 1, flags >> 4 & 1,
                intensity, lang, webhookUrl);
        registry.close();
    }

    private void insertValid() {
        String parentHook = "https://discord.com/api/webhooks/300/" + TOKEN;
        insert(100, 100, null, 10L, 0b11001, 4, "ja_jp", parentHook);
        // Thread sharing the parent's webhook, as stored by legacy Core with ?thread_id=.
        insert(101, 100, 101L, 10L, 0b00110, 0, "EN_US", parentHook + "?thread_id=101");
        insert(102, 102, null, null, 0b00001, 9, "ko_Hanja", null);
    }

    private int run(String... extra) {
        List<String> args = new ArrayList<>(List.of("--source", "sqlite", "--source-path", this.db.toString(), "--base-url", origin()));
        args.addAll(List.of(extra));
        return RemoteDestinationImport.run(args.toArray(String[]::new), ENV, true);
    }

    private RemoteDestinationImport.Summary execute(String... extra) throws Exception {
        List<String> args = new ArrayList<>(List.of("--source-path", this.db.toString(), "--base-url", origin()));
        args.addAll(List.of(extra));
        RemoteDestinationImport.Options options = RemoteDestinationImport.Options.parse(args.toArray(String[]::new), ENV);
        options.backoffMillis = 1;
        return RemoteDestinationImport.execute(options, ENV, true);
    }

    @Test void convertsLegacyRowsAndSkipsWhatTheWorkerWouldReject() throws Exception {
        insertValid();
        insert(200, 201, null, 10L, 1, 1, "ja_jp", null);                    // channel identity mismatch
        insert(201, 201, 202L, 10L, 1, 1, "ja_jp", null);                    // thread target != thread_id
        insert(203, 203, 203L, 10L, 1, 1, "ja_jp", null);                    // thread whose parent is itself
        insert(204, 204, null, 10L, 1, 1, "fr_fr", null);                    // unknown lang
        insert(205, 205, null, 10L, 1, 1, "ja_jp", "https://example.com/x"); // not a Discord webhook
        insert(206, 206, null, 10L, 1, 1, "ja_jp", "https://discord.com/api/webhooks/300/short");
        insert(207, 207, null, 10L, 1, 1, "ja_jp", "https://discord.com/api/webhooks/300/" + TOKEN + "/github");
        insert(208, 208, null, 10L, 1, 10, "ja_jp", null);                   // intensity out of range
        insert(209, 209, null, 10L, 1, 1, "ja_jp", "https://discord.com/api/webhooks/abc/" + TOKEN);

        ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(this.db);
        RemoteDestinationImport.Conversion conversion;
        try { conversion = RemoteDestinationImport.convert(registry.getDsl()); } finally { registry.close(); }

        assertThat(conversion.skipped()).containsOnlyKeys(200L, 201L, 203L, 204L, 205L, 206L, 207L, 208L, 209L);
        assertThat(conversion.skipped().values()).noneMatch(reason -> reason.contains(TOKEN));
        assertThat((JsonNode) JSON.valueToTree(conversion.destinations())).isEqualTo(JSON.readTree("""
                [{"targetId":"100","channelId":"100","threadId":null,"guildId":"10","eewAlert":true,"eewPrediction":false,
                  "eewDecimation":false,"quakeInfo":true,"tsunami":true,"minIntensity":4,"lang":"ja_jp",
                  "webhook":{"id":"300","token":"%1$s"}},
                 {"targetId":"101","channelId":"100","threadId":"101","guildId":"10","eewAlert":false,"eewPrediction":true,
                  "eewDecimation":true,"quakeInfo":false,"tsunami":false,"minIntensity":0,"lang":"en_us",
                  "webhook":{"id":"300","token":"%1$s"}},
                 {"targetId":"102","channelId":"102","threadId":null,"guildId":null,"eewAlert":true,"eewPrediction":false,
                  "eewDecimation":false,"quakeInfo":false,"tsunami":false,"minIntensity":9,"lang":"ko_hanja","webhook":null}]
                """.formatted(TOKEN)));
    }

    @Test void importsInTargetOrderedChunksAndVerifiesTheSnapshot() throws Exception {
        insertValid();
        insert(150, 150, null, 10L, 1, 1, "xx", null);

        RemoteDestinationImport.Summary summary = execute("--chunk-size", "2");

        assertThat(summary).isEqualTo(new RemoteDestinationImport.Summary(3, 3, 1, 0));
        assertThat(this.bodies).hasSize(2);
        assertThat(JSON.readTree(this.bodies.get(0)).get("destinations")).extracting(d -> d.get("targetId").textValue())
                .containsExactly("100", "101");
        assertThat(JSON.readTree(this.bodies.get(1)).get("destinations")).extracting(d -> d.get("targetId").textValue())
                .containsExactly("102");
        assertThat(this.badCredentials).isFalse();

        // A re-run is a no-op upsert: everything is sent again, nothing is affected.
        assertThat(execute()).isEqualTo(new RemoteDestinationImport.Summary(3, 0, 1, 0));
    }

    @Test void retriesServerErrorsAndStopsOnClientErrors() throws Exception {
        insertValid();
        this.statuses.addAll(List.of(503, 429));
        assertThat(execute()).isEqualTo(new RemoteDestinationImport.Summary(3, 3, 0, 0));
        assertThat(this.bodies).hasSize(3).allMatch(this.bodies.getFirst()::equals);

        this.bodies.clear();
        this.statuses.add(400);
        assertThat(run("--no-verify")).isEqualTo(1);
        assertThat(this.bodies).hasSize(1);
    }

    @Test void giveUpAfterMaxAttempts() throws Exception {
        insertValid();
        this.statuses.addAll(List.of(500, 500, 500));
        assertThat(run("--max-attempts", "2", "--no-verify")).isEqualTo(1);
        assertThat(this.bodies).hasSize(2);
    }

    @Test void verificationReportsRowsTheWorkerDoesNotHold() throws Exception {
        insertValid();
        assertThat(execute().verifyFailures()).isZero();
        // Rows changed or lost on the Worker after the import, plus one only the Worker knows about.
        this.remote.put("102", ((ObjectNode) this.remote.get("102").deepCopy()).put("quakeInfo", true));
        this.remote.remove("100");
        this.remote.put("900", ((ObjectNode) this.remote.get("102").deepCopy()).put("targetId", "900").put("channelId", "900"));

        ChannelRegistrySql registry = ChannelRegistrySql.forSQLite(this.db);
        List<Map<String, Object>> sent;
        try { sent = RemoteDestinationImport.convert(registry.getDsl()).destinations(); } finally { registry.close(); }
        RemoteDestinationImport.Options options = RemoteDestinationImport.Options.parse(new String[]{"--base-url", origin()}, ENV);
        assertThat(RemoteDestinationImport.verify(sent, options, ID, SECRET, true)).isEqualTo(2);
    }

    @Test void dryRunSendsNothingAndNeedsNoBaseUrl() {
        insertValid();
        int code = RemoteDestinationImport.run(new String[]{"--source-path", this.db.toString(), "--dry-run"}, key -> null, true);
        assertThat(code).isZero();
        assertThat(this.bodies).isEmpty();
    }

    @Test void rejectsMissingSourceAndBadArguments() {
        assertThat(RemoteDestinationImport.run(new String[]{"--source-path", this.dir.resolve("none.db").toString(), "--dry-run"},
                key -> null, true)).isEqualTo(1);
        assertThat(this.dir.resolve("none.db")).doesNotExist();
        assertThat(RemoteDestinationImport.run(new String[]{"--source-path", this.db.toString()}, ENV, true)).isEqualTo(1);
        assertThat(run("--chunk-size", "501")).isEqualTo(1);
        assertThat(run("--unknown")).isEqualTo(1);
        assertThat(this.bodies).isEmpty();
    }
}
