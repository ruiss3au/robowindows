package org.robowindows.app;

/** Product-owned presentation modes, independent of CPU and timing policy. */
final class PresentationPolicy {
    static final int SOFTWARE = 0;
    static final int GPU = 1;
    private PresentationPolicy() {}
    static boolean allowed(int value) { return value == SOFTWARE || value == GPU; }
    static int forLaunch(int saved, boolean debug, boolean experimental, boolean recovery) {
        if (!allowed(saved)) throw new IllegalArgumentException("Unknown presentation mode");
        return recovery ? SOFTWARE : saved;
    }
}
