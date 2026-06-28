package net.teamfruit.eewbot;

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
}
