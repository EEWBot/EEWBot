package net.teamfruit.eewbot.quakeinfo;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.QuakeInfoStore;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.*;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static net.teamfruit.eewbot.testutil.QuakeReports.*;
import static org.assertj.core.api.Assertions.*;

class QuakeInfoSnapshotPublisherTest {
    static ConfigV2.QuakeInfoPublisher config() {
        return Codecs.GSON.fromJson("{\"reconcileIntervalMillis\":30,\"retryInitialMillis\":30,\"retryMaxMillis\":100}", ConfigV2.QuakeInfoPublisher.class);
    }
    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    static QuakeInfo quake(int serial) throws Exception {
        return (QuakeInfo) report(JMAXmlType.VXSE53, serial, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1600 + serial));
    }
    static final class Fake implements QuakeInfoSnapshotClient {
        final List<QuakeInfoSnapshot> creates = new CopyOnWriteArrayList<>();
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger leases = new AtomicInteger();
        volatile Latest latest = new Latest(null,null,0);
        volatile boolean failCreate;
        volatile boolean uncertainCommit;
        volatile boolean fenced;
        volatile boolean closed;
        volatile QuakeInfoSnapshot pending;
        @Override public long lease(String id) { leases.incrementAndGet(); return 1; }
        @Override public void create(QuakeInfoSnapshot snapshot) {
            creates.add(snapshot);
            if (fenced) throw new Failure(409,"publisher_fenced");
            if (failCreate) throw new Failure(503,"transport_or_invalid_response");
            pending = snapshot;
        }
        @Override public void put(String id,String lang,int index,String body) { }
        @Override public void commit(String id) {
            commits.incrementAndGet(); latest = new Latest(id,pending.contentHash(),1);
            if (uncertainCommit) { uncertainCommit = false; throw new Failure(0,"transport_or_invalid_response"); }
        }
        @Override public Latest latest() { reads.incrementAndGet(); return latest; }
        @Override public void close() { closed = true; }
    }
    @Test void pushesPreloadedStateAndSubsequentStoreUpdatesThenCloses() throws Exception {
        var store = new QuakeInfoStore(); store.putReport(quake(1)); var client = new Fake();
        try (var publisher = new QuakeInfoSnapshotPublisher(store,QuakeInfoSnapshotTest.context(store),client,config())) {
            publisher.start(); await(() -> client.commits.get() == 1);
            store.putReport(quake(2)); await(() -> client.commits.get() == 2);
            assertThat(client.creates.getLast().messages()).hasSize(5);
            assertThat(client.creates.getLast().snapshotId()).isNotEqualTo(client.creates.getFirst().snapshotId());
        }
        assertThat(client.closed).isTrue();
    }
    @Test void failuresUseSameIdAndBytesWhileGatewayUpdatesRemainAvailable() throws Exception {
        var store = new QuakeInfoStore(); store.putReport(quake(1)); var client = new Fake(); client.failCreate = true;
        try (var publisher = new QuakeInfoSnapshotPublisher(store,QuakeInfoSnapshotTest.context(store),client,config())) {
            publisher.start(); await(() -> client.creates.size() >= 2);
            assertThat(client.creates.get(0)).isSameAs(client.creates.get(1));
            store.putReport(quake(2)); assertThat(store.getLatestReport()).isPresent();
            client.failCreate = false; await(() -> client.commits.get() >= 1);
            assertThat(client.creates.getLast().metadataJson()).contains("\"serial\":2");
        }
    }
    @Test void reconcilesAmbiguousCommitBeforeRetryAndRepairsMissingRemoteState() throws Exception {
        var store = new QuakeInfoStore(); store.putReport(quake(1)); var client = new Fake(); client.uncertainCommit = true;
        try (var publisher = new QuakeInfoSnapshotPublisher(store,QuakeInfoSnapshotTest.context(store),client,config())) {
            publisher.start(); await(() -> client.commits.get() == 1 && client.reads.get() > 0);
            assertThat(client.creates).hasSize(1);
            client.latest = new QuakeInfoSnapshotClient.Latest(null,null,0);
            await(() -> client.commits.get() == 2);
        }
    }
    @Test void stopsWhenFencedAndNeverAcquiresAnotherEpoch() throws Exception {
        var store = new QuakeInfoStore(); store.putReport(quake(1)); var client = new Fake(); client.fenced = true;
        try (var publisher = new QuakeInfoSnapshotPublisher(store,QuakeInfoSnapshotTest.context(store),client,config())) {
            publisher.start(); await(publisher::isFenced);
            store.putReport(quake(2)); publisher.requestSync();
            assertThat(client.leases).hasValue(1);
            assertThat(client.commits).hasValue(0);
        }
    }
    @Test void disabledByDefaultAndIndependentOfDatabaseMode() {
        assertThat(new ConfigV2().getQuakeInfoPublisher().isEnabled()).isFalse();
        var settings = Codecs.GSON.fromJson("{\"database\":{\"type\":\"postgresql\"},\"quakeInfoPublisher\":{\"enabled\":true}}",ConfigV2.class);
        assertThat(settings.getQuakeInfoPublisher().isEnabled()).isTrue();
        assertThat(settings.getDatabase().getType()).isEqualTo("postgresql");
        var store = new QuakeInfoStore();
        try (var manager = new net.teamfruit.eewbot.gateway.GatewayManager(null,new ConfigV2(),0,null,null,null,store,null)) {
            // No credentials and no HTTP client: disabled initialization must not try to send anything.
            manager.initQuakeInfoPublisher(QuakeInfoSnapshotTest.context(store));
        }
    }

    @Test void synchronizesAfterLongFeedInitialLoadCompletes() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        String origin = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        String fixture = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/jmaxml/vxse53/case1.xml"));
        server.createContext("/",exchange -> {
            String body = exchange.getRequestURI().getPath().equals("/eqvol_l.xml")
                    ? "<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry><id>test</id><title>震源・震度に関する情報</title><updated>2025-12-08T14:26:00Z</updated><link href=\"" + origin + "report.xml\"/></entry></feed>"
                    : fixture;
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var store = new QuakeInfoStore(); var client = new Fake(); var completed = new AtomicInteger();
        String threadName = Thread.currentThread().getName();
        try (var http = java.net.http.HttpClient.newHttpClient();
             var publisher = new QuakeInfoSnapshotPublisher(store,QuakeInfoSnapshotTest.context(store),client,config())) {
            publisher.start();
            new net.teamfruit.eewbot.gateway.JMAXmlLGateway(http,store,origin,() -> {
                assertThat(store.getLatestReport()).isPresent(); completed.incrementAndGet(); publisher.requestSync();
            }).run();
            await(() -> client.commits.get() > 0);
            assertThat(completed).hasValue(1);
            assertThat(client.creates.getLast().metadataJson()).contains("VXSE53");
        } finally { server.stop(0); Thread.currentThread().setName(threadName); }
    }

    @Test void discardsRenderIfReferenceStateChangesMidwayThroughFiveLanguages() throws Exception {
        var store = new QuakeInfoStore(); var client = new Fake();
        var first = org.mockito.Mockito.spy(quake(1)); var next = quake(2);
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call -> {
            if (once.compareAndSet(false,true)) store.putReport(next);
            return call.callRealMethod();
        }).when(first).createEmbeds(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        store.putReport(first);
        try (var publisher = new QuakeInfoSnapshotPublisher(store,QuakeInfoSnapshotTest.context(store),client,config())) {
            publisher.start(); await(() -> client.commits.get() > 0);
            assertThat(client.creates).allSatisfy(snapshot -> assertThat(snapshot.metadataJson()).contains("\"serial\":2"));
        }
    }
}
