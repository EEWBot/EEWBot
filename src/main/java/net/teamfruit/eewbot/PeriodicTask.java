package net.teamfruit.eewbot;

public final class PeriodicTask {

    private PeriodicTask() {
    }

    /**
     * A scheduled executor silently cancels a periodic task once one run throws.
     * Wrap every periodic task so a failed run is logged and the next run still happens.
     */
    public static Runnable guarded(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                Log.logger.error("Periodic task {} failed; it will run again at the next interval", name, e);
            }
        };
    }
}
