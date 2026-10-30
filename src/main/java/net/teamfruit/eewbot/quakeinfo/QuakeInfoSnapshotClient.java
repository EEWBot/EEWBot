package net.teamfruit.eewbot.quakeinfo;

/** Transport seam for deterministic tests without Access or Discord credentials. */
public interface QuakeInfoSnapshotClient extends AutoCloseable {
    /** accepted=false means the stored latest is strictly newer and the request was ignored. */
    record Result(boolean accepted, long revision) {}
    Result publish(QuakeInfoSnapshot snapshot);
    @Override void close();

    final class Failure extends RuntimeException {
        private final int status;
        private final String code;
        public Failure(int status, String code) {
            super("QuakeInfo synchronization failed (status " + status + ", " + code + ")");
            this.status = status;
            this.code = code;
        }
        public int status() { return this.status; }
        public String code() { return this.code; }
        /** Only 5xx and transport failures (status 0) are worth retrying with the same body. */
        public boolean retryable() { return this.status == 0 || this.status >= 500; }
    }
}
