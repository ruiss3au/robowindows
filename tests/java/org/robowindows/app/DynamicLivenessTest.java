package org.robowindows.app;

public final class DynamicLivenessTest {
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        DynamicLiveness proof = DynamicLiveness.parse("runs=10 frames=2");
        require(proof.provesRunning(1000), "exact liveness threshold");
        require(!proof.provesRunning(999), "one second is required");
        require(!DynamicLiveness.parse("runs=9 frames=99").provesRunning(1000),
                "run threshold is required");
        require(!DynamicLiveness.parse("runs=99 frames=1").provesRunning(1000),
                "frame threshold is required");
        require(!DynamicLiveness.parse("runs=bad frames=2").provesRunning(1000),
                "malformed proof is rejected");
        System.out.println("Dynamic liveness checks passed");
    }
}
