package org.robowindows.app;

/** Detects a silent child bridge or an emulator thread that stops completing calls. */
final class DynamicProgressWatchdog {
    static final long TIMEOUT_MILLIS = 30_000;

    private long lastResponseMillis;
    private long lastProgressMillis;
    private long lastRunCalls = -1;
    private boolean paused;

    DynamicProgressWatchdog(long nowMillis) {
        lastResponseMillis = nowMillis;
        lastProgressMillis = nowMillis;
    }

    void observe(long nowMillis, long runCalls) {
        lastResponseMillis = nowMillis;
        if (lastRunCalls < 0 || runCalls != lastRunCalls) {
            lastRunCalls = runCalls;
            lastProgressMillis = nowMillis;
        }
    }

    void setPaused(boolean value, long nowMillis) {
        paused = value;
        if (!value) {
            lastResponseMillis = nowMillis;
            lastProgressMillis = nowMillis;
        }
    }

    boolean timedOut(long nowMillis) {
        return !paused && (elapsed(nowMillis, lastResponseMillis) >= TIMEOUT_MILLIS ||
                elapsed(nowMillis, lastProgressMillis) >= TIMEOUT_MILLIS);
    }

    private static long elapsed(long nowMillis, long thenMillis) {
        return Math.max(0, nowMillis - thenMillis);
    }
}
