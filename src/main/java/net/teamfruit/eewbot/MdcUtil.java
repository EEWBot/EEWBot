package net.teamfruit.eewbot;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.slf4j.MDC;

import java.util.Map;

public final class MdcUtil {

    private MdcUtil() {
    }

    /**
     * Wraps a Runnable to capture and restore MDC context across thread boundaries.
     * After execution, the previous MDC state is restored (not cleared).
     */
    public static Runnable wrapWithMdc(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (captured != null) MDC.setContextMap(captured);
            else MDC.clear();
            try {
                task.run();
            } finally {
                if (previous != null) MDC.setContextMap(previous);
                else MDC.clear();
            }
        };
    }

    /**
     * Wraps a FutureCallback to capture and restore MDC context for each callback invocation.
     * After each callback, the previous MDC state is restored (not cleared).
     */
    public static <T> FutureCallback<T> wrapWithMdc(FutureCallback<T> callback) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return new FutureCallback<>() {
            private void withMdc(Runnable action) {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (captured != null) MDC.setContextMap(captured);
                else MDC.clear();
                try {
                    action.run();
                } finally {
                    if (previous != null) MDC.setContextMap(previous);
                    else MDC.clear();
                }
            }

            @Override
            public void completed(T result) {
                withMdc(() -> callback.completed(result));
            }

            @Override
            public void failed(Exception ex) {
                withMdc(() -> callback.failed(ex));
            }

            @Override
            public void cancelled() {
                withMdc(callback::cancelled);
            }
        };
    }
}
