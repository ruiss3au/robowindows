package org.robowindows.app;

public final class RuntimeTimingPolicyTest {
    public static void main(String[] args) {
        require(RuntimeTimingPolicy.forExperimentalMachine(false) == RuntimeTimingPolicy.LEGACY,
                "stable machines retain legacy timing");
        require(RuntimeTimingPolicy.forExperimentalMachine(true) ==
                RuntimeTimingPolicy.BALANCED_100_MS,
                "experimental machines select balanced timing under either CPU core");
        require(RuntimeTimingPolicy.isAllowed(RuntimeTimingPolicy.LEGACY), "legacy allowlisted");
        require(RuntimeTimingPolicy.isAllowed(RuntimeTimingPolicy.BALANCED_100_MS),
                "balanced allowlisted");
        require(!RuntimeTimingPolicy.isAllowed(-1), "negative ID rejected");
        require(!RuntimeTimingPolicy.isAllowed(2), "unknown ID rejected");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
