package net.teamfruit.eewbot.entity.discord;

import net.teamfruit.eewbot.entity.webhooksender.WebhookSenderRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordWebhookRequestTest {

    @Test
    void targetSnapshotsCannotChangePendingOrPreparedDeliveries() {
        String first = "https://discord.com/api/webhooks/123/first?thread_id=456";
        String second = "https://discord.com/api/webhooks/789/second";
        DiscordWebhookRequest request = new DiscordWebhookRequest("ja_jp", DiscordWebhook.builder().build())
                .addTarget(first);
        List<String> snapshot = request.getTargets();
        WebhookSenderRequest prepared = WebhookSenderRequest.from(request);

        assertThatThrownBy(() -> snapshot.add(second)).isInstanceOf(UnsupportedOperationException.class);
        request.addTarget(second);

        assertThat(snapshot).containsExactly(first + "&with_components=true");
        assertThat(prepared.targets()).containsExactly(first + "&with_components=true");
        assertThat(request.getTargets()).containsExactly(
                first + "&with_components=true", second + "?with_components=true");
    }
}
