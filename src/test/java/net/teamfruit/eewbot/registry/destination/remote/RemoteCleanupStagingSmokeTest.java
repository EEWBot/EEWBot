package net.teamfruit.eewbot.registry.destination.remote;

import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in; fixed staging host, no Discord login, only empty/no-op D1 mutations. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "EEWBOT_REMOTE_CLEANUP_STAGING_SMOKE", matches = "true")
class RemoteCleanupStagingSmokeTest {
    @Test void stagingWriterCompatibilityAudienceSeparationAndNoopRevision() {
        var config = Codecs.GSON.fromJson("{\"baseUrl\":\"https://api-staging.eewbot.net\",\"cleanupEnabled\":true}", ConfigV2.Remote.class);
        String readId = System.getenv("EEWBOT_CF_ACCESS_CLIENT_ID");
        String readSecret = System.getenv("EEWBOT_CF_ACCESS_CLIENT_SECRET");
        String writeId = System.getenv("EEWBOT_CF_ACCESS_CLEANUP_CLIENT_ID");
        String writeSecret = System.getenv("EEWBOT_CF_ACCESS_CLEANUP_CLIENT_SECRET");
        try (var reader = new RemoteConfigApiClient(config, readId, readSecret);
             var writer = new RemoteCleanupApiClient(config, writeId, writeSecret);
             var wrongWriter = new RemoteCleanupApiClient(config, readId, readSecret);
             var wrongReader = new RemoteConfigApiClient(config, writeId, writeSecret)) {
            reader.checkCompatibility();
            writer.checkCompatibility();
            assertThatThrownBy(wrongWriter::checkCompatibility).isInstanceOf(RemoteConfigException.class)
                    .hasMessageMatching(".*status (401|403|302).*" );
            assertThatThrownBy(wrongReader::checkCompatibility).isInstanceOf(RemoteConfigException.class)
                    .hasMessageMatching(".*status (401|403|302).*" );
            var source = new RemoteDestinationConfigSource(reader);
            long revision = source.getRevision();
            assertThat(writer.mutate("destinations", Map.of("targetIds", List.of()))).isZero();
            assertThat(writer.mutate("webhooks", Map.of("targets", List.of()))).isZero();
            assertThat(writer.mutate("webhook-ids", Map.of("webhookIds", List.of()))).isZero();
            assertThat(source.getRevision()).isEqualTo(revision);
        }
    }
}
