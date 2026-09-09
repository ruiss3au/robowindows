package org.robowindows.app;

public final class DynamicReadinessTest {
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        int evidence = DynamicReadiness.add(0, DynamicReadiness.DESKTOP);
        evidence = DynamicReadiness.add(evidence, DynamicReadiness.KEYBOARD);
        require(!DynamicReadiness.complete(evidence), "mouse evidence remains required");
        evidence = DynamicReadiness.add(evidence, DynamicReadiness.CAPTURED_MOUSE);
        require(DynamicReadiness.complete(evidence), "all explicit evidence is complete");
        require(DynamicReadiness.add(evidence, DynamicReadiness.DESKTOP) == evidence,
                "evidence is idempotent");
        boolean rejected = false;
        try {
            DynamicReadiness.add(0, DynamicReadiness.KEYBOARD);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected, "desktop confirmation must come first");
        System.out.println("Dynamic readiness checks passed");
    }
}
