package org.robowindows.app;

public final class DynamicAttemptStateTest {
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        require(DynamicAttemptState.mayTransition(DynamicAttemptState.PREPARED,
                DynamicAttemptState.EXECUTING), "prepared executes");
        require(DynamicAttemptState.mayTransition(DynamicAttemptState.EXECUTING,
                DynamicAttemptState.RUNNING), "executing runs");
        require(DynamicAttemptState.mayTransition(DynamicAttemptState.RUNNING,
                DynamicAttemptState.CLOSED_CLEAN), "running closes cleanly");
        require(DynamicAttemptState.mayTransition(DynamicAttemptState.RUNNING,
                DynamicAttemptState.NEEDS_CHECK), "running can be quarantined");
        require(!DynamicAttemptState.mayTransition(DynamicAttemptState.PREPARED,
                DynamicAttemptState.RUNNING), "cannot skip executing");
        require(!DynamicAttemptState.mayTransition(DynamicAttemptState.CLOSED_CLEAN,
                DynamicAttemptState.EXECUTING), "closed attempt cannot restart");
        require(DynamicAttemptState.isUncertain(DynamicAttemptState.EXECUTING),
                "executing is uncertain");
        require(!DynamicAttemptState.isUncertain(DynamicAttemptState.PREPARED),
                "prepared has no native handoff");
        System.out.println("Dynamic attempt state checks passed");
    }
}
