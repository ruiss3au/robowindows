package org.robowindows.app;

public final class DynamicLivenessTest {
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        DynamicLiveness proof = DynamicLiveness.parse("runs=10 frames=2 decoder=DynRec " +
                "current=Halt dyn=7 normal=1 pf=2 halt=3 other=0 " +
                "pfenq=12 pfret=9 pfdepth=3 pfmax=4 pfwipe=2 pfrecover=1 " +
                "pfprep=5 pfdeliver=4 pfgate=3 iret=6 df=1 reset=2");
        require(proof.provesRunning(1000), "exact liveness threshold");
        require(proof.usesDynRec(), "exact dynamic decoder evidence");
        require(proof.dynRecSamples == 7 && proof.normalSamples == 1 &&
                proof.pageFaultSamples == 2 && proof.haltSamples == 3 &&
                proof.otherSamples == 0,
                "decoder residency parsed");
        require(proof.pageFaultEnqueued == 12 && proof.pageFaultCompleted == 9 &&
                proof.pageFaultDepth == 3 && proof.pageFaultHighWater == 4 &&
                proof.pageFaultWipes == 2 && proof.pageFaultRecoveries == 1,
                "page-fault lifecycle parsed");
        require(proof.exceptionPageFaultPrepared == 5 &&
                proof.exceptionPageFaultDelivered == 4 &&
                proof.exceptionPageFaultGateEntered == 3 &&
                proof.exceptionIretExecuted == 6 &&
                proof.exceptionDoubleFaultDelivered == 1 && proof.guestResets == 2,
                "exception lifecycle parsed");
        require(proof.residencySummary().contains("Configured DynRec · Now Halt"),
                "configured and current decoders are summarized");
        require(proof.residencySummary().contains("PFQ 12/9 · Depth 3/4 · Wipe 2/1"),
                "page-fault lifecycle is summarized");
        require(proof.residencySummary().contains(
                "Exception PF 5/4/3 · IRET 6 · DF 1 · Reset 2"),
                "exception lifecycle is summarized");
        require(!proof.provesRunning(999), "one second is required");
        require(!DynamicLiveness.parse("runs=9 frames=99").provesRunning(1000),
                "run threshold is required");
        require(!DynamicLiveness.parse("runs=99 frames=1").provesRunning(1000),
                "frame threshold is required");
        require(!DynamicLiveness.parse("runs=bad frames=2").provesRunning(1000),
                "malformed proof is rejected");
        require(DynamicLiveness.parse("runs=10 frames=2 dyn=-5").dynRecSamples == 0,
                "negative residency is clamped");
        require(DynamicLiveness.parse("runs=10 frames=2 pfenq=-1 pfdepth=-2")
                .pageFaultDepth == 0, "negative page-fault aggregates are clamped");
        require(DynamicLiveness.parse(
                "pfprep=-1 pfdeliver=-2 pfgate=-3 iret=-4 df=-5 reset=-6 halt=-7")
                .exceptionPageFaultPrepared == 0, "negative exception aggregates are clamped");
        require(!DynamicLiveness.parse("runs=10 frames=2 decoder=Normal").usesDynRec(),
                "normal fallback is detected");
        System.out.println("Dynamic liveness checks passed");
    }
}
