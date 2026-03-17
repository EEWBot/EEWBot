package net.teamfruit.eewbot;

import org.apache.hc.core5.concurrent.FutureCallback;
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
        Runnable wrapped = MdcUtil.wrapWithMdc(() -> {
            assertThat(MDC.get("gateway")).isEqualTo("inner");
        });

        // Restore outer context before running
        MDC.put("gateway", "outer");
        wrapped.run();

        // After execution, outer context should be restored
        assertThat(MDC.get("gateway")).isEqualTo("outer");
    }

    @Test
    void wrapWithMdc_restoresEmptyMdcWhenPreviousWasEmpty() {
        MDC.put("gateway", "test");
        Runnable wrapped = MdcUtil.wrapWithMdc(() -> {
            assertThat(MDC.get("gateway")).isEqualTo("test");
        });

        MDC.clear();
        wrapped.run();

        // MDC should be empty (restored to empty state)
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void wrapWithMdc_futureCallback_propagatesContextOnCompleted() {
        MDC.put("gateway", "dmdata");
        MDC.put("event.type", "eew");

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        FutureCallback<String> original = new FutureCallback<>() {
            @Override
            public void completed(String result) {
                captured.set(MDC.getCopyOfContextMap());
            }

            @Override
            public void failed(Exception ex) {
            }

            @Override
            public void cancelled() {
            }
        };

        FutureCallback<String> wrapped = MdcUtil.wrapWithMdc(original);

        MDC.clear();
        wrapped.completed("ok");

        assertThat(captured.get())
                .containsEntry("gateway", "dmdata")
                .containsEntry("event.type", "eew");
    }

    @Test
    void wrapWithMdc_futureCallback_propagatesContextOnFailed() {
        MDC.put("gateway", "kmoni");

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        FutureCallback<String> original = new FutureCallback<>() {
            @Override
            public void completed(String result) {
            }

            @Override
            public void failed(Exception ex) {
                captured.set(MDC.getCopyOfContextMap());
            }

            @Override
            public void cancelled() {
            }
        };

        FutureCallback<String> wrapped = MdcUtil.wrapWithMdc(original);

        MDC.clear();
        wrapped.failed(new RuntimeException("test"));

        assertThat(captured.get()).containsEntry("gateway", "kmoni");
    }

    @Test
    void wrapWithMdc_futureCallback_propagatesContextOnCancelled() {
        MDC.put("gateway", "jma-xml");

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        FutureCallback<String> original = new FutureCallback<>() {
            @Override
            public void completed(String result) {
            }

            @Override
            public void failed(Exception ex) {
            }

            @Override
            public void cancelled() {
                captured.set(MDC.getCopyOfContextMap());
            }
        };

        FutureCallback<String> wrapped = MdcUtil.wrapWithMdc(original);

        MDC.clear();
        wrapped.cancelled();

        assertThat(captured.get()).containsEntry("gateway", "jma-xml");
    }

    @Test
    void wrapWithMdc_futureCallback_restoresPreviousMdc() {
        MDC.put("gateway", "inner");
        FutureCallback<String> wrapped = MdcUtil.wrapWithMdc(new FutureCallback<>() {
            @Override
            public void completed(String result) {
            }

            @Override
            public void failed(Exception ex) {
            }

            @Override
            public void cancelled() {
            }
        });

        MDC.put("gateway", "outer");
        wrapped.completed("ok");

        assertThat(MDC.get("gateway")).isEqualTo("outer");
    }
}
