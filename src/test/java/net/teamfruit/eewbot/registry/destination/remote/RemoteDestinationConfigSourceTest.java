package net.teamfruit.eewbot.registry.destination.remote;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static net.teamfruit.eewbot.registry.destination.remote.RemoteApiFixture.*;
import static org.assertj.core.api.Assertions.*;

class RemoteDestinationConfigSourceTest {
    private RemoteApiFixture api;
    private RemoteConfigApiClient client;
    private RemoteDestinationConfigSource source;

    @BeforeEach void setup() throws Exception {
        this.api = new RemoteApiFixture();
        this.client = this.api.client();
        this.source = new RemoteDestinationConfigSource(this.client);
    }
    @AfterEach void close() { this.client.close(); this.api.close(); }

    @Test void threadChannelNullsFlagsAndExactSnowflakes() {
        ObjectNode thread = destination();
        ObjectNode channel = destination();
        channel.put("targetId", "9223372036854775807");
        channel.put("channelId", "9223372036854775807");
        for (String name : new String[]{"threadId", "guildId", "webhookUrl", "webhookId"}) channel.putNull(name);
        for (String name : new String[]{"eewAlert", "eewPrediction", "eewDecimation", "quakeInfo", "tsunami"}) {
            channel.put(name, !thread.get(name).booleanValue());
        }
        channel.put("lang", "en_us");
        this.api.snapshot(9007199254740991L, thread, channel);

        var snapshot = this.source.loadSnapshot();
        assertThat(snapshot.getRevision()).isEqualTo(9007199254740991L);
        assertThat(snapshot.size()).isEqualTo(2);
        var t = snapshot.get(TARGET);
        assertThat(t.targetId()).isEqualTo(TARGET);
        assertThat(t.channelId()).isEqualTo(1234567890123456790L);
        assertThat(t.threadId()).isEqualTo(TARGET);
        assertThat(t.guildId()).isEqualTo(1234567890123456789L);
        assertThat(t.eewAlert()).isTrue();
        assertThat(t.eewPrediction()).isFalse();
        assertThat(t.eewDecimation()).isTrue();
        assertThat(t.quakeInfo()).isFalse();
        assertThat(t.tsunami()).isTrue();
        assertThat(t.minIntensity()).isEqualTo(SeismicIntensity.FOUR);
        assertThat(t.webhook().getUrl()).isEqualTo(URL);
        assertThat(t.webhook().id()).isEqualTo(1234567890123456792L);
        assertThat(t.webhook().token()).isEqualTo("synthetic-token");
        var c = snapshot.get(Long.MAX_VALUE);
        assertThat(c.channelId()).isEqualTo(Long.MAX_VALUE);
        assertThat(c.threadId()).isNull();
        assertThat(c.guildId()).isNull();
        assertThat(c.webhook()).isNull();
        assertThat(c.lang()).isEqualTo("en_us");
        assertThat(c.eewAlert()).isFalse();
        assertThat(c.eewPrediction()).isTrue();
        assertThat(c.eewDecimation()).isFalse();
        assertThat(c.quakeInfo()).isTrue();
        assertThat(c.tsunami()).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7,8,9})
    void everyIntensityCode(int code) {
        var d = destination();
        d.put("minIntensity", code);
        this.api.snapshot(0, d);
        assertThat(this.source.loadSnapshot().get(TARGET).minIntensity()).isEqualTo(SeismicIntensity.fromCode(code));
    }

    @ParameterizedTest @ValueSource(strings = {"targetId", "channelId", "threadId", "guildId", "eewAlert", "eewPrediction",
            "eewDecimation", "quakeInfo", "tsunami", "minIntensity", "lang", "webhookUrl", "webhookId"})
    void everyFieldIsRequired(String field) {
        var d = destination();
        d.remove(field);
        this.api.snapshot(1, d);
        assertInvalid();
    }

    @ParameterizedTest @CsvSource({"eewAlert,1", "eewPrediction,\"true\"", "eewDecimation,null", "quakeInfo,0", "tsunami,{}",
            "minIntensity,-1", "minIntensity,10", "minIntensity,4.5", "minIntensity,\"4\"", "lang,null", "lang,4",
            "targetId,1234567890123456791", "channelId,1.2", "guildId,1", "threadId,true", "webhookUrl,1", "webhookId,1"})
    void invalidTypesAndIntensityRejectSnapshot(String field, String json) throws Exception {
        var d = destination();
        d.set(field, JSON.readTree(json));
        this.api.snapshot(1, d);
        assertInvalid();
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "01", "1.0", "1e3", " 123", "9223372036854775808", "18446744073709551615", "18446744073709551616"})
    void unsupportedSnowflakesRejectWholeSnapshot(String id) {
        var d = destination();
        d.put("guildId", id);
        this.api.snapshot(1, destination(), d);
        assertInvalid();
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "1.5", "1.0", "\"1\"", "null", "true", "9007199254740992", "9223372036854775808"})
    void revisionsNeverLosePrecisionOrTruncate(String revision) {
        this.api.reply(REVISION, 200, "{\"revision\":" + revision + "}");
        assertThatThrownBy(this.source::getRevision).isInstanceOf(RemoteConfigException.class);
        this.api.reply(SNAPSHOT, 200, "{\"revision\":" + revision + ",\"destinations\":[]}");
        assertInvalid();
    }

    @ParameterizedTest @ValueSource(strings = {"https://discord.com/api/webhooks/1234567890123456793/synthetic-token",
            "https://discord.com/api/webhooks/1234567890123456792/synthetic-token?thread_id=18446744073709551615",
            "https://discord.com/api/webhooks/1234567890123456792/synthetic-token?thread_id=1&thread_id=2",
            "https://discord.com/api/webhooks/1234567890123456792/synthetic-token#fragment",
            "https://elsewhere.example/api/webhooks/1234567890123456792/synthetic-token"})
    void inconsistentOrInvalidWebhooksAreSanitized(String url) {
        var d = destination();
        d.put("webhookUrl", url);
        this.api.snapshot(1, d);
        assertInvalid();
    }

    @Test void nullWebhookMustHaveNullWebhookId() {
        var d = destination();
        d.putNull("webhookUrl");
        this.api.snapshot(1, d);
        assertInvalid();
        d = destination();
        d.putNull("webhookId");
        this.api.snapshot(1, d);
        assertInvalid();
    }

    @Test void duplicateTargetIdAndInvalidSecondRecordRejectEntireSnapshot() {
        this.api.snapshot(1, destination(), destination());
        assertInvalid();
        var bad = destination();
        bad.put("targetId", "10");
        bad.put("threadId", "10");
        bad.put("eewAlert", "true");
        this.api.snapshot(1, destination(), bad);
        assertInvalid();
        this.api.snapshot(1, destination());
        assertThat(this.source.loadSnapshot().size()).isEqualTo(1);
    }

    @Test void snapshotUsesItsOwnRevisionRatherThanEarlierPoll() {
        this.api.reply(REVISION, 200, "{\"revision\":1}");
        assertThat(this.source.getRevision()).isEqualTo(1);
        this.api.snapshot(2, destination());
        assertThat(this.source.loadSnapshot().getRevision()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rollbackLatchesUntilOperatorRestart(boolean snapshotRollback) {
        this.api.snapshot(10, destination());
        this.source.loadSnapshot();
        this.api.reply(REVISION, 200, "{\"revision\":0}");
        this.api.snapshot(0);
        assertThatThrownBy(() -> { if (snapshotRollback) this.source.loadSnapshot(); else this.source.getRevision(); })
                .hasMessageContaining("rollback").hasMessageContaining("restart");
        int requests = this.api.total();
        assertThatThrownBy(this.source::getRevision).hasMessageContaining("restart");
        assertThatThrownBy(this.source::loadSnapshot).hasMessageContaining("restart");
        assertThat(this.api.total()).isEqualTo(requests);
        assertThat(new RemoteDestinationConfigSource(this.client).loadSnapshot().getRevision()).isZero();
    }

    @Test void rollbackBetweenTwoPollsIsAlsoDetected() {
        this.api.reply(REVISION, 200, "{\"revision\":5}");
        assertThat(this.source.getRevision()).isEqualTo(5);
        this.api.reply(REVISION, 200, "{\"revision\":4}");
        assertThatThrownBy(this.source::getRevision).hasMessageContaining("rollback");
    }

    private void assertInvalid() {
        assertThatThrownBy(this.source::loadSnapshot).isInstanceOf(RemoteConfigException.class).hasNoCause()
                .hasMessageNotContaining(URL).hasMessageNotContaining("synthetic-token");
    }
}
