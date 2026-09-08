package org.robowindows.app;

/** Pure transition rules for the durable dynamic-attempt journal. */
final class DynamicAttemptState {
    static final String PREPARED = "prepared";
    static final String EXECUTING = "executing";
    static final String RUNNING = "running";
    static final String CLOSED_CLEAN = "closed-clean";
    static final String NEEDS_CHECK = "needs-check";
    static final String BLOCKED = "blocked";

    private DynamicAttemptState() {}

    static boolean mayTransition(String from, String to) {
        if (PREPARED.equals(from)) {
            return EXECUTING.equals(to) || BLOCKED.equals(to);
        }
        if (EXECUTING.equals(from)) {
            return RUNNING.equals(to) || NEEDS_CHECK.equals(to) || BLOCKED.equals(to);
        }
        if (RUNNING.equals(from)) {
            return CLOSED_CLEAN.equals(to) || NEEDS_CHECK.equals(to) || BLOCKED.equals(to);
        }
        return false;
    }

    static boolean isUncertain(String state) {
        return EXECUTING.equals(state) || RUNNING.equals(state) || NEEDS_CHECK.equals(state);
    }
}
