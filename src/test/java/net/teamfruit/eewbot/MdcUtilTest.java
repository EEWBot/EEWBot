package net.teamfruit.eewbot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcUtilTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void wrapWithMdc_propagatesContextToAnotherThread() throws Exception {
        MDC.put("gateway", "dmdata");
        MDC.put("event.id", "12345");

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        Runnable wrapped = MdcUtil.wrapWithMdc(() -> captured.set(MDC.getCopyOfContextMap()));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(wrapped).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(captured.get())
                .containsEntry("gateway", "dmdata")
                .containsEntry("event.id", "12345");
    }

    @Test
    void wrapWithMdc_restoresPreviousMdcAfterExecution() {
        // Set up "outer" MDC context
        MDC.put("gateway", "outer");

        // Capture a different context
        MDC.put("gateway", "inner");
        Runnable wrapped = MdcUtil.wrapWithMdc(() -> assertThat(MDC.get("gateway")).isEqualTo("inner"));

        // Restore outer context before running
        MDC.put("gateway", "outer");
        wrapped.run();

        // After execution, outer context should be restored
        assertThat(MDC.get("gateway")).isEqualTo("outer");
    }

    @Test
    void wrapWithMdc_restoresEmptyMdcWhenPreviousWasEmpty() {
        MDC.put("gateway", "test");
        Runnable wrapped = MdcUtil.wrapWithMdc(() -> assertThat(MDC.get("gateway")).isEqualTo("test"));

        MDC.clear();
        wrapped.run();

        // MDC should be empty (restored to empty state)
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

}
