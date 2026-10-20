package net.teamfruit.eewbot.gateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class EewConcurrentNumberingTest {

    @Test
    void discardedPlansDoNotConsumeGroupsOrNumbersOrMarkExistingEventsConcurrent() {
        EewConcurrentNumbering numbering = new EewConcurrentNumbering(() -> 0);
        EewConcurrentNumbering.Plan failedFirst = numbering.prepare("a", true, id -> true);
        EewConcurrentNumbering.Plan first = numbering.prepare("b", true, id -> true);
        assertThat(first.number()).isEqualTo(failedFirst.number());
        numbering.commit(first);

        EewConcurrentNumbering.Plan failedOverlap = numbering.prepare("c", true, id -> true);
        EewConcurrentNumbering.Plan unchanged = numbering.prepare("b", true, id -> true);
        assertThat(unchanged.number().everConcurrent()).isFalse();
        numbering.commit(unchanged);
        EewConcurrentNumbering.Plan retry = numbering.prepare("c", true, id -> true);
        assertThat(retry.number()).isEqualTo(failedOverlap.number());
        numbering.commit(retry);
        numbering.commit(numbering.prepare("b", false, id -> true));
        numbering.commit(numbering.prepare("c", false, id -> true));

        EewConcurrentNumbering.Plan failedNextGroup = numbering.prepare("d", true, id -> true);
        EewConcurrentNumbering.Plan nextGroup = numbering.prepare("e", true, id -> true);
        assertThat(nextGroup.number()).isEqualTo(failedNextGroup.number());
        assertThat(nextGroup.number().groupId()).isEqualTo(first.number().groupId() + 1);
        assertThat(nextGroup.number().index()).isEqualTo(1);
        assertThat(nextGroup.number().everConcurrent()).isFalse();
    }

    @Test
    void activeAssignmentsSurviveHistoryCapacityAndTtlLimits() {
        AtomicLong time = new AtomicLong();
        // No inactive history can survive this capacity, but active assignments must survive.
        EewConcurrentNumbering numbering = new EewConcurrentNumbering(time::get, 0);
        numbering.commit(numbering.prepare("a", true, id -> true));
        EewConcurrentNumbering.Plan second = numbering.prepare("b", true, id -> true);
        numbering.commit(second);
        for (int i = 0; i < 20; i++)
            numbering.commit(numbering.prepare("terminal-" + i, false, id -> true));
        time.addAndGet(Duration.ofHours(2).toNanos());

        EewConcurrentNumbering.Plan retained = numbering.prepare("b", true, id -> true);
        assertThat(retained.number()).isEqualTo(second.number());
        numbering.commit(retained);
        EewConcurrentNumbering.Plan first = numbering.prepare("a", true, id -> true);
        assertThat(first.number().index()).isEqualTo(1);
        assertThat(first.number().everConcurrent()).isTrue();
        numbering.commit(first);
        numbering.commit(numbering.prepare("b", false, id -> true));
        EewConcurrentNumbering.Plan third = numbering.prepare("c", true, id -> true);
        assertThat(third.number().index()).isEqualTo(3);
        assertThat(third.number().groupId()).isEqualTo(second.number().groupId());
    }

    @Test
    void endedHistoryExpiresAfterOneHourWithoutRenewalByLateReports() {
        AtomicLong time = new AtomicLong();
        EewConcurrentNumbering numbering = new EewConcurrentNumbering(time::get);
        numbering.commit(numbering.prepare("a", true, id -> true));
        numbering.commit(numbering.prepare("b", true, id -> true));
        numbering.commit(numbering.prepare("a", false, id -> true));
        EewConcurrentNumbering.Plan ended = numbering.prepare("b", false, id -> true);
        numbering.commit(ended);
        time.addAndGet(Duration.ofMinutes(59).toNanos());
        EewConcurrentNumbering.Plan delayed = numbering.prepare("b", false, id -> false);
        assertThat(delayed.number()).isEqualTo(ended.number());
        numbering.commit(delayed);
        time.addAndGet(Duration.ofMinutes(1).toNanos());

        EewConcurrentNumbering.Plan expired = numbering.prepare("b", false, id -> false);
        assertThat(expired.number().groupId()).isNotEqualTo(ended.number().groupId());
        assertThat(expired.number().index()).isEqualTo(1);
        assertThat(expired.number().everConcurrent()).isFalse();
    }
}
