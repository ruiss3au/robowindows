package org.robowindows.app;

/** Single-use picker ownership; a result must never mount into a later session. */
final class SessionMediaRequest {
    private long generation = -1;
    void begin(long current) { generation = current; }
    void clear() { generation = -1; }
    boolean consume(long current, boolean active) {
        boolean matches = active && generation >= 0 && generation == current;
        clear();
        return matches;
    }
}
