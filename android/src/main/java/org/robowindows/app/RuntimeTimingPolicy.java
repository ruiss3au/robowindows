package org.robowindows.app;

/** Internal allowlist for frontend timing; this is not a user-facing setting. */
final class RuntimeTimingPolicy {
    static final int LEGACY = 0;
    static final int BALANCED_100_MS = 1;

    private RuntimeTimingPolicy() {}

    static int forExperimentalMachine(boolean experimental) {
        return experimental ? BALANCED_100_MS : LEGACY;
    }

    static boolean isAllowed(int policy) {
        return policy == LEGACY || policy == BALANCED_100_MS;
    }
}
