package org.robowindows.app;

import android.os.Process;
import android.os.SystemClock;

/** Ensures a finished emulator service cannot be retained with native globals. */
final class IsolatedProcessExit {
    private static final long RESULT_DELIVERY_GRACE_MILLIS = 250;

    private IsolatedProcessExit() {}

    static void afterResult() {
        final int pid = Process.myPid();
        Thread exit = new Thread(() -> {
            SystemClock.sleep(RESULT_DELIVERY_GRACE_MILLIS);
            Process.killProcess(pid);
        }, "robowindows-isolated-exit");
        exit.setDaemon(false);
        exit.start();
    }
}
