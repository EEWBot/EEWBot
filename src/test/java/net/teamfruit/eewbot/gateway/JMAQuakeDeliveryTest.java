package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.*;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.external.ExternalData;
import net.teamfruit.eewbot.entity.external.QuakeInfoExternalData;
import net.teamfruit.eewbot.entity.jma.*;
import net.teamfruit.eewbot.registry.config.ConfigV2;
import net.teamfruit.eewbot.registry.destination.delivery.DeliverySnapshot;
import net.teamfruit.eewbot.registry.destination.model.ChannelFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;

import static net.teamfruit.eewbot.testutil.QuakeReports.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JMAQuakeDeliveryTest {

    private final QuakeInfoStore store = new QuakeInfoStore(Clock.fixed(START, ZoneOffset.UTC), () -> 0);
    private final EEWService service = mock(EEWService.class);
    private final ExternalWebhookService external = mock(ExternalWebhookService.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private GatewayManager manager;

    @BeforeEach
    void setUp() {
        when(this.executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return CompletableFuture.completedFuture(null);
        });
        var scheduler = mock(ScheduledExecutorService.class);
        this.manager = new GatewayManager(this.service, new ConfigV2(), 1L, scheduler, scheduler,
                null, null, null, this.store, this.external, this.executor);
    }

    @Test
    void cancellationUsesAcceptedMaximumForMatchingTypeAndPreservesNormalFilters() throws Exception {
        deliver(report(JMAXmlType.VXSE51, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1200)));
        deliver(report(JMAXmlType.VXSE51, 2, JMAInfoType.訂正, SeismicIntensity.THREE, START.plusSeconds(1250)));
        deliver(report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1500)));
        var cancel51 = report(JMAXmlType.VXSE51, 3, JMAInfoType.取消, null, START.plusSeconds(1600));
        deliver(cancel51);
        var cancel53 = report(JMAXmlType.VXSE53, 2, JMAInfoType.取消, null, START.plusSeconds(1700));
        deliver(cancel53);
        var filters = ArgumentCaptor.forClass(ChannelFilter.class);
        verify(this.service, times(5)).sendMessage(filters.capture(), any(AbstractJMAReport.class));
        assertThat(filters.getAllValues()).extracting(ChannelFilter::getIntensity)
                .containsExactly(SeismicIntensity.FIVE_PLUS, SeismicIntensity.THREE, SeismicIntensity.FOUR,
                        SeismicIntensity.FIVE_PLUS, SeismicIntensity.FOUR);
        assertThat(filters.getAllValues().get(3).test(channel(true, SeismicIntensity.FIVE_PLUS))).isTrue();
        assertThat(filters.getAllValues().get(3).test(channel(true, SeismicIntensity.SIX_MINUS))).isFalse();
        assertThat(filters.getAllValues().get(3).test(channel(false, SeismicIntensity.ONE))).isFalse();
        verify(this.external).sendExternalWebhook((ExternalData) cancel51);
        verify(this.external).sendExternalWebhook((ExternalData) cancel53);
    }

    @Test
    void startupReferenceLoadingAndPartialDeliveryHistoryCannotRestrictCancellation() throws Exception {
        var reference = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1500));
        this.store.putReport(reference); // Same reference-only API used by JMAXmlLGateway.
        deliver(report(JMAXmlType.VXSE53, 2, JMAInfoType.発表, SeismicIntensity.THREE, START.plusSeconds(1600)));
        deliver(report(JMAXmlType.VXSE53, 3, JMAInfoType.取消, null, START.plusSeconds(1700)));
        var filters = ArgumentCaptor.forClass(ChannelFilter.class);
        verify(this.service, times(2)).sendMessage(filters.capture(), any(AbstractJMAReport.class));
        ChannelFilter cancellation = filters.getAllValues().getLast();
        assertThat(cancellation.isIntensityPresent()).isFalse();
        assertThat(cancellation.test(channel(true, SeismicIntensity.SEVEN))).isTrue();
        assertThat(cancellation.test(channel(false, SeismicIntensity.ONE))).isFalse();
    }

    @Test
    void rejectedSubmissionDoesNotRecordDeliveryHistory() throws Exception {
        when(this.executor.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("closed"));
        var normal = report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1500));
        assertThatThrownBy(() -> deliver(normal)).isInstanceOf(RejectedExecutionException.class);
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).maxIntensity()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = JMAXmlType.class, names = {"VXSE51", "VXSE52", "VXSE53"})
    void cancellationDoesNotReadAbsentBodyFieldsAndUsesUnfilteredSubscription(JMAXmlType type) throws Exception {
        var cancel = report(type, 2, JMAInfoType.取消, null, START.plusSeconds(1800));
        deliver(cancel);
        var filters = ArgumentCaptor.forClass(ChannelFilter.class);
        verify(this.service).sendMessage(filters.capture(), any(AbstractJMAReport.class));
        assertThat(filters.getValue().isIntensityPresent()).isFalse();
        var dto = (QuakeInfoExternalData) ((ExternalData) cancel).toExternalDto();
        assertThat(dto.getOriginTime()).isNull();
        assertThat(dto.getDepth()).isNull();
        assertThat(dto.getFreeFormComment()).isNull();
        assertThat(dto.getForecastComment()).isNull();
        assertThat(dto.getInfoType()).isEqualTo("取消");
    }

    private void deliver(AbstractJMAReport report) {
        this.store.putReport((QuakeInfo) report);
        this.manager.handleJMAReport(report);
    }

    private static DeliverySnapshot.DeliveryChannel channel(boolean quakeInfo, SeismicIntensity minimum) {
        return new DeliverySnapshot.DeliveryChannel(1L, 1L, null, null, false, false, false,
                quakeInfo, false, minimum, "ja_jp", null);
    }
}
