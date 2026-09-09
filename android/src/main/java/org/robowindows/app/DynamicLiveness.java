package org.robowindows.app;

/** Redacted aggregate liveness proof required before a journal becomes running. */
final class DynamicLiveness {
    static final long MIN_ELAPSED_MS = 1000;
    static final long MIN_RUN_CALLS = 10;
    static final long MIN_PUBLISHED_FRAMES = 2;

    final long runCalls;
    final long publishedFrames;
    final String decoder;
    final String currentDecoder;
    final long dynRecSamples;
    final long normalSamples;
    final long pageFaultSamples;
    final long haltSamples;
    final long otherSamples;
    final long pageFaultEnqueued;
    final long pageFaultCompleted;
    final long pageFaultDepth;
    final long pageFaultHighWater;
    final long pageFaultWipes;
    final long pageFaultRecoveries;
    final long exceptionPageFaultPrepared;
    final long exceptionPageFaultDelivered;
    final long exceptionPageFaultGateEntered;
    final long exceptionIretExecuted;
    final long exceptionDoubleFaultDelivered;
    final long guestResets;

    DynamicLiveness(long runCalls, long publishedFrames) {
        this(runCalls, publishedFrames, "Unknown", "Unknown", 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    DynamicLiveness(long runCalls, long publishedFrames, String decoder) {
        this(runCalls, publishedFrames, decoder, "Unknown", 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    DynamicLiveness(long runCalls, long publishedFrames, String decoder,
            String currentDecoder, long dynRecSamples, long normalSamples,
            long pageFaultSamples, long haltSamples, long otherSamples, long pageFaultEnqueued,
            long pageFaultCompleted, long pageFaultDepth, long pageFaultHighWater,
            long pageFaultWipes, long pageFaultRecoveries, long exceptionPageFaultPrepared,
            long exceptionPageFaultDelivered, long exceptionPageFaultGateEntered,
            long exceptionIretExecuted, long exceptionDoubleFaultDelivered, long guestResets) {
        this.runCalls = Math.max(0, runCalls);
        this.publishedFrames = Math.max(0, publishedFrames);
        this.decoder = decoder == null ? "Unknown" : decoder;
        this.currentDecoder = currentDecoder == null ? "Unknown" : currentDecoder;
        this.dynRecSamples = Math.max(0, dynRecSamples);
        this.normalSamples = Math.max(0, normalSamples);
        this.pageFaultSamples = Math.max(0, pageFaultSamples);
        this.haltSamples = Math.max(0, haltSamples);
        this.otherSamples = Math.max(0, otherSamples);
        this.pageFaultEnqueued = Math.max(0, pageFaultEnqueued);
        this.pageFaultCompleted = Math.max(0, pageFaultCompleted);
        this.pageFaultDepth = Math.max(0, pageFaultDepth);
        this.pageFaultHighWater = Math.max(0, pageFaultHighWater);
        this.pageFaultWipes = Math.max(0, pageFaultWipes);
        this.pageFaultRecoveries = Math.max(0, pageFaultRecoveries);
        this.exceptionPageFaultPrepared = Math.max(0, exceptionPageFaultPrepared);
        this.exceptionPageFaultDelivered = Math.max(0, exceptionPageFaultDelivered);
        this.exceptionPageFaultGateEntered = Math.max(0, exceptionPageFaultGateEntered);
        this.exceptionIretExecuted = Math.max(0, exceptionIretExecuted);
        this.exceptionDoubleFaultDelivered = Math.max(0, exceptionDoubleFaultDelivered);
        this.guestResets = Math.max(0, guestResets);
    }

    static DynamicLiveness parse(String encoded) {
        long runs = 0;
        long frames = 0;
        long dyn = 0;
        long normal = 0;
        long pageFault = 0;
        long halt = 0;
        long other = 0;
        long enqueued = 0;
        long completed = 0;
        long depth = 0;
        long highWater = 0;
        long wipes = 0;
        long recoveries = 0;
        long prepared = 0;
        long delivered = 0;
        long gateEntered = 0;
        long iretExecuted = 0;
        long doubleFault = 0;
        long resets = 0;
        String decoder = "Unknown";
        String current = "Unknown";
        if (encoded != null) {
            for (String part : encoded.split(" ")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) continue;
                try {
                    if ("runs".equals(pair[0])) runs = Long.parseLong(pair[1]);
                    if ("frames".equals(pair[0])) frames = Long.parseLong(pair[1]);
                    if ("decoder".equals(pair[0])) decoder = pair[1];
                    if ("current".equals(pair[0])) current = pair[1];
                    if ("dyn".equals(pair[0])) dyn = Long.parseLong(pair[1]);
                    if ("normal".equals(pair[0])) normal = Long.parseLong(pair[1]);
                    if ("pf".equals(pair[0])) pageFault = Long.parseLong(pair[1]);
                    if ("halt".equals(pair[0])) halt = Long.parseLong(pair[1]);
                    if ("other".equals(pair[0])) other = Long.parseLong(pair[1]);
                    if ("pfenq".equals(pair[0])) enqueued = Long.parseLong(pair[1]);
                    if ("pfret".equals(pair[0])) completed = Long.parseLong(pair[1]);
                    if ("pfdepth".equals(pair[0])) depth = Long.parseLong(pair[1]);
                    if ("pfmax".equals(pair[0])) highWater = Long.parseLong(pair[1]);
                    if ("pfwipe".equals(pair[0])) wipes = Long.parseLong(pair[1]);
                    if ("pfrecover".equals(pair[0])) recoveries = Long.parseLong(pair[1]);
                    if ("pfprep".equals(pair[0])) prepared = Long.parseLong(pair[1]);
                    if ("pfdeliver".equals(pair[0])) delivered = Long.parseLong(pair[1]);
                    if ("pfgate".equals(pair[0])) gateEntered = Long.parseLong(pair[1]);
                    if ("iret".equals(pair[0])) iretExecuted = Long.parseLong(pair[1]);
                    if ("df".equals(pair[0])) doubleFault = Long.parseLong(pair[1]);
                    if ("reset".equals(pair[0])) resets = Long.parseLong(pair[1]);
                } catch (NumberFormatException ignored) {
                    return new DynamicLiveness(0, 0, "Unknown");
                }
            }
        }
        return new DynamicLiveness(runs, frames, decoder, current, dyn, normal,
                pageFault, halt, other, enqueued, completed, depth, highWater, wipes,
                recoveries, prepared, delivered, gateEntered, iretExecuted, doubleFault, resets);
    }

    boolean provesRunning(long elapsedMs) {
        return elapsedMs >= MIN_ELAPSED_MS && runCalls >= MIN_RUN_CALLS &&
                publishedFrames >= MIN_PUBLISHED_FRAMES;
    }

    boolean usesDynRec() {
        return "DynRec".equals(decoder);
    }

    String residencySummary() {
        return "Configured " + decoder + " · Now " + currentDecoder + " · DynRec " +
                dynRecSamples + " · PF " + pageFaultSamples + " · Normal " + normalSamples +
                " · Halt " + haltSamples + " · Other " + otherSamples + " · PFQ " + pageFaultEnqueued + "/" +
                pageFaultCompleted + " · Depth " + pageFaultDepth + "/" +
                pageFaultHighWater + " · Wipe " + pageFaultWipes + "/" +
                pageFaultRecoveries + " · Exception PF " + exceptionPageFaultPrepared + "/" +
                exceptionPageFaultDelivered + "/" + exceptionPageFaultGateEntered +
                " · IRET " + exceptionIretExecuted + " · DF " + exceptionDoubleFaultDelivered +
                " · Reset " + guestResets;
    }
}
