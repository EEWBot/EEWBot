package net.teamfruit.eewbot;

import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.jma.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;

import static net.teamfruit.eewbot.testutil.QuakeReports.*;
import static org.assertj.core.api.Assertions.assertThat;

class QuakeInfoStoreTest {

    private final AtomicLong ticks = new AtomicLong();
    private final QuakeInfoStore store = new QuakeInfoStore(Clock.fixed(START, ZoneOffset.UTC), this.ticks::get);

    @Test
    void retainsEveryTypeAndLatestNormalAcrossCorrectionsCancellationsAndLateReports() throws Exception {
        var first = report(JMAXmlType.VXSE51, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1200));
        var normal = report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1500));
        var correction = report(JMAXmlType.VXSE53, 2, JMAInfoType.訂正, SeismicIntensity.FOUR, START.plusSeconds(1600));
        var cancel = report(JMAXmlType.VXSE53, 3, JMAInfoType.取消, null, START.plusSeconds(1700));
        this.store.putReport((QuakeInfo) first);
        this.store.putReport((QuakeInfo) normal);
        this.store.putReport((QuakeInfo) correction);
        this.store.putReport((QuakeInfo) cancel);
        this.store.putReport((QuakeInfo) normal);
        assertThat(this.store.getReport(EVENT, JMAXmlType.VXSE51)).contains((QuakeInfo) first);
        assertThat(this.store.getReport(EVENT, JMAXmlType.VXSE53)).contains((QuakeInfo) correction);
        assertThat(this.store.getLatestReport(EVENT, JMAXmlType.VXSE53)).contains((QuakeInfo) cancel);
        assertThat(this.store.getLatestReport()).contains((QuakeInfo) cancel);
        assertThat(this.store.getMaxIntensity(EVENT, JMAXmlType.VXSE53)).contains(SeismicIntensity.FIVE_PLUS);
    }

    @Test
    void correctionAndCancellationWinTiesWithoutLosingNormalReference() throws Exception {
        var normal = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1500));
        var correction = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.訂正, SeismicIntensity.THREE, START.plusSeconds(1500));
        var cancel = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.取消, null, START.plusSeconds(1500));
        this.store.putReport(normal);
        this.store.putReport(correction);
        this.store.putReport(cancel);
        this.store.putReport(normal);
        assertThat(this.store.getReport(EVENT, JMAXmlType.VXSE53)).contains(correction);
        assertThat(this.store.getLatestReport(EVENT, JMAXmlType.VXSE53)).contains(cancel);
    }

    @Test
    void expirationIs24HoursFromLastEventWriteAndReadsDoNotExtendIt() throws Exception {
        var first = (QuakeInfo) report(JMAXmlType.VXSE51, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1200));
        this.store.putReport(first);
        this.ticks.set(Duration.ofHours(23).toNanos());
        assertThat(this.store.getReport(EVENT, JMAXmlType.VXSE51)).contains(first);
        var next = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FOUR, START.plusSeconds(1500));
        this.store.putReport(next);
        this.ticks.set(Duration.ofHours(46).toNanos());
        assertThat(this.store.getReport(EVENT, JMAXmlType.VXSE51)).contains(first);
        this.ticks.set(Duration.ofHours(47).toNanos());
        assertThat(this.store.getReport(EVENT, JMAXmlType.VXSE53)).isEmpty();
        assertThat(EEWFilterClassifier.classifyQuakeInfoCancellation(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53))
                .isIntensityPresent()).isFalse();
    }

    @Test
    void referenceLoadingNeverBecomesDeliveryHistoryAndMaximaAreSeparateByType() throws Exception {
        var reference = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.SEVEN, START.plusSeconds(1500));
        this.store.putReport(reference);
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isFalse();
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).maxIntensity()).isNull();
        var first = (QuakeInfo) report(JMAXmlType.VXSE51, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1200));
        var lower = (QuakeInfo) report(JMAXmlType.VXSE51, 2, JMAInfoType.訂正, SeismicIntensity.THREE, START.plusSeconds(1250));
        this.store.recordDelivery(first, SeismicIntensity.FIVE_PLUS);
        this.store.recordDelivery(lower, SeismicIntensity.THREE);
        this.store.recordDelivery(reference, SeismicIntensity.FOUR);
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE51).maxIntensity()).isEqualTo(SeismicIntensity.FIVE_PLUS);
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).maxIntensity()).isEqualTo(SeismicIntensity.FOUR);
    }

    @Test
    void restartPartialSerialHistoryAndUnknownIntensityAlwaysFallback() throws Exception {
        var first = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.THREE, START.plusSeconds(1500));
        var restarted = new QuakeInfoStore(Clock.fixed(START.plus(Duration.ofHours(1)), ZoneOffset.UTC), this.ticks::get);
        restarted.recordDelivery(first, SeismicIntensity.THREE);
        assertThat(restarted.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isFalse();
        this.store.recordDelivery(first, SeismicIntensity.FIVE_PLUS);
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isTrue();
        var gap = (QuakeInfo) report(JMAXmlType.VXSE53, 3, JMAInfoType.発表, SeismicIntensity.THREE, START.plusSeconds(1600));
        this.store.recordDelivery(gap, SeismicIntensity.THREE);
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isFalse();
        this.store.recordDelivery(first, SeismicIntensity.FIVE_PLUS);
        assertThat(this.store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isFalse();
        var unknown = new QuakeInfoStore(Clock.fixed(START, ZoneOffset.UTC), this.ticks::get);
        unknown.recordDelivery(first, null);
        assertThat(unknown.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isFalse();
    }

    @Test
    void recreatedHistoryAfterExpirationDoesNotRegainCompletenessFromSerialOne() throws Exception {
        Clock later = Clock.fixed(START.plus(Duration.ofHours(25)), ZoneOffset.UTC);
        Clock changing = org.mockito.Mockito.mock(Clock.class);
        org.mockito.Mockito.when(changing.instant()).thenReturn(START).thenAnswer(invocation -> later.instant());
        var store = new QuakeInfoStore(changing, this.ticks::get);
        var first = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1500));
        store.recordDelivery(first, SeismicIntensity.FIVE_PLUS);
        this.ticks.set(Duration.ofHours(25).toNanos());
        assertThat(store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isFalse();
        store.putReport(first);
        store.recordDelivery(first, SeismicIntensity.THREE);
        assertThat(store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53).complete()).isFalse();
    }

    @Test
    void maximumSizeEvictionUsesUnfilteredCancellationFallback() throws Exception {
        var store = new QuakeInfoStore(Clock.fixed(START, ZoneOffset.UTC), this.ticks::get, 0);
        var first = (QuakeInfo) report(JMAXmlType.VXSE53, 1, JMAInfoType.発表, SeismicIntensity.FIVE_PLUS, START.plusSeconds(1500));
        store.putReport(first);
        store.recordDelivery(first, SeismicIntensity.FIVE_PLUS);
        store.cleanUp();
        assertThat(store.getReport(EVENT, JMAXmlType.VXSE53)).isEmpty();
        assertThat(EEWFilterClassifier.classifyQuakeInfoCancellation(store.getDeliveryHistory(EVENT, JMAXmlType.VXSE53))
                .isIntensityPresent()).isFalse();
    }
}
