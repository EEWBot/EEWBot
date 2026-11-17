package net.teamfruit.eewbot;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodicTaskTest {

    @Test
    void failedRunDoesNotCancelLaterRuns() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger runs = new AtomicInteger();
            CountDownLatch thirdRun = new CountDownLatch(3);
            scheduler.scheduleAtFixedRate(PeriodicTask.guarded("test", () -> {
                thirdRun.countDown();
                if (runs.getAndIncrement() == 0)
                    throw new IllegalStateException("first run fails");
                if (runs.get() == 2)
                    throw new StackOverflowError("second run fails with an Error");
            }), 0, 10, TimeUnit.MILLISECONDS);

            assertTrue(thirdRun.await(5, TimeUnit.SECONDS));
        } finally {
            scheduler.shutdownNow();
        }
    }
}
