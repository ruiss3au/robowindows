package org.robowindows.app;

/** Explicit human-observed readiness evidence, separate from runner liveness. */
final class DynamicReadiness {
    static final int DESKTOP = 1;
    static final int KEYBOARD = 1 << 1;
    static final int CAPTURED_MOUSE = 1 << 2;
    static final int COMPLETE = DESKTOP | KEYBOARD | CAPTURED_MOUSE;

    private DynamicReadiness() {}

    static int add(int current, int evidence) {
        if (evidence != DESKTOP && evidence != KEYBOARD && evidence != CAPTURED_MOUSE) {
            throw new IllegalArgumentException("Unknown dynamic readiness evidence");
        }
        if (evidence != DESKTOP && (current & DESKTOP) == 0) {
            throw new IllegalStateException("Confirm a responsive desktop first");
        }
        return (current | evidence) & COMPLETE;
    }

    static boolean complete(int evidence) {
        return (evidence & COMPLETE) == COMPLETE;
    }
}
