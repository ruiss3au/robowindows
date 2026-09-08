package org.robowindows.app;

/** Redacted aggregate liveness proof required before a journal becomes running. */
final class DynamicLiveness {
    static final long MIN_ELAPSED_MS = 1000;
    static final long MIN_RUN_CALLS = 10;
    static final long MIN_PUBLISHED_FRAMES = 2;

    final long runCalls;
    final long publishedFrames;

    DynamicLiveness(long runCalls, long publishedFrames) {
        this.runCalls = Math.max(0, runCalls);
        this.publishedFrames = Math.max(0, publishedFrames);
    }

    static DynamicLiveness parse(String encoded) {
        long runs = 0;
        long frames = 0;
        if (encoded != null) {
            for (String part : encoded.split(" ")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) continue;
                try {
                    if ("runs".equals(pair[0])) runs = Long.parseLong(pair[1]);
                    if ("frames".equals(pair[0])) frames = Long.parseLong(pair[1]);
                } catch (NumberFormatException ignored) {
                    return new DynamicLiveness(0, 0);
                }
            }
        }
        return new DynamicLiveness(runs, frames);
    }

    boolean provesRunning(long elapsedMs) {
        return elapsedMs >= MIN_ELAPSED_MS && runCalls >= MIN_RUN_CALLS &&
                publishedFrames >= MIN_PUBLISHED_FRAMES;
    }
}
