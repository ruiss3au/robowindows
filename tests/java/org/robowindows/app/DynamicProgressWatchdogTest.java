package org.robowindows.app;

public final class DynamicProgressWatchdogTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DynamicProgressWatchdog silent = new DynamicProgressWatchdog(1000);
        require(!silent.timedOut(30_999), "silent runner gets full window");
        require(silent.timedOut(31_000), "silent runner times out");

        DynamicProgressWatchdog stuck = new DynamicProgressWatchdog(0);
        stuck.observe(100, 10);
        for (long now = 1_000; now < 30_100; now += 1_000) stuck.observe(now, 10);
        require(stuck.timedOut(30_100), "responses without emulator progress time out");

        DynamicProgressWatchdog advancing = new DynamicProgressWatchdog(0);
        for (long now = 1_000; now <= 120_000; now += 1_000) {
            advancing.observe(now, now);
            require(!advancing.timedOut(now), "advancing runner remains healthy");
        }

        DynamicProgressWatchdog paused = new DynamicProgressWatchdog(0);
        paused.observe(1_000, 10);
        paused.setPaused(true, 2_000);
        require(!paused.timedOut(120_000), "pause suspends watchdog");
        paused.setPaused(false, 120_000);
        require(!paused.timedOut(149_999), "resume gets a fresh window");
        require(paused.timedOut(150_000), "no progress after resume times out");

        DynamicProgressWatchdog restarted = new DynamicProgressWatchdog(0);
        restarted.observe(1_000, 500);
        restarted.observe(20_000, 0);
        require(!restarted.timedOut(49_999), "counter reset is fresh restart progress");
        require(restarted.timedOut(50_000), "stalled restarted runner times out");
        System.out.println("Dynamic progress watchdog checks passed");
    }
}
