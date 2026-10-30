package net.teamfruit.eewbot.quakeinfo;

/** Transport seam for deterministic tests without Access or Discord credentials. */
public interface QuakeInfoSnapshotClient extends AutoCloseable {
    record Latest(String snapshotId, String contentHash, long publisherEpoch) {}
    long lease(String publisherId);
    void create(QuakeInfoSnapshot snapshot);
    void put(String snapshotId, String lang, int index, String body);
    void commit(String snapshotId);
    Latest latest();
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
    }
}
