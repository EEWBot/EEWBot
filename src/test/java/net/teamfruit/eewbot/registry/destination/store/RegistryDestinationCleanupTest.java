package net.teamfruit.eewbot.registry.destination.store;

import net.teamfruit.eewbot.registry.destination.DestinationAdminRegistry;


import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import net.teamfruit.eewbot.registry.destination.delivery.DeliveryTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RegistryDestinationCleanupTest {

    @Test
    void unavailableDestinationsKeepPerTargetWritesAndOneSave() throws Exception {
        DestinationAdminRegistry admin = mock(DestinationAdminRegistry.class);

        new RegistryDestinationCleanup(admin).destinationsUnavailable(List.of(1L, 2L));

        var order = inOrder(admin);
        order.verify(admin).remove(1L);
        order.verify(admin).remove(2L);
        order.verify(admin).save();
        verifyNoMoreInteractions(admin);
    }

    @Test
    void unavailableWebhooksKeepPerTargetWritesAndOneSave() throws Exception {
        DestinationAdminRegistry admin = mock(DestinationAdminRegistry.class);

        new RegistryDestinationCleanup(admin).webhooksUnavailable(List.of(1L, 2L));

        var order = inOrder(admin);
        order.verify(admin).setWebhook(1L, null);
        order.verify(admin).setWebhook(2L, null);
        order.verify(admin).save();
        verifyNoMoreInteractions(admin);
    }

    @Test
    void webhookSenderCleanupKeepsOneBatchOperation() {
        DestinationAdminRegistry admin = mock(DestinationAdminRegistry.class);
        List<String> urls = List.of("https://discord.com/api/webhooks/123/test?thread_id=456");
        when(admin.clearWebhookByUrls(urls)).thenReturn(2);

        assertThat(new RegistryDestinationCleanup(admin).webhookUrlsUnavailable(urls)).isEqualTo(2);

        verify(admin).clearWebhookByUrls(urls);
        verifyNoMoreInteractions(admin);
    }

    @Test
    void expectedWebhookOverloadPreservesLocalSqlAdapterSemantics() throws Exception {
        DestinationAdminRegistry admin = mock(DestinationAdminRegistry.class);
        new RegistryDestinationCleanup(admin).webhooksUnavailable(Map.of(
                1L, new DeliveryTarget(1L, "ja_jp", "https://discord.com/api/webhooks/123/synthetic")));
        var order = inOrder(admin);
        order.verify(admin).setWebhook(1L, null);
        order.verify(admin).save();
        verifyNoMoreInteractions(admin);
    }
}
