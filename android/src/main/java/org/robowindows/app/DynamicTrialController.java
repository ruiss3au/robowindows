package org.robowindows.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;

import java.io.IOException;

/** Internal owner of an isolated dynamic trial and its durable journal. */
final class DynamicTrialController implements DynamicTrialClient.Listener {
    interface Listener {
        void onStatus(int status, String error, String residency);
    }

    private final MachineStore machineStore;
    private final DynamicTrialClient client;
    private final Listener listener;
    private DynamicAttempt attempt;
    private MachineProfile profile;
    private boolean guestShutdownObserved;
    private boolean finished;
    private boolean childStarted;
    private boolean desiredPaused;
    private Surface latestSurface;
    private long startedAtMillis;
    private long pausedAtMillis = -1;
    private long pausedMillis;
    private long residencyLoggedAtMillis;
    private DynamicProgressWatchdog progressWatchdog;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable livenessPoll = this::pollLiveness;

    DynamicTrialController(Context context, Listener listener) {
        machineStore = new MachineStore(context);
        client = new DynamicTrialClient(context, this);
        this.listener = listener;
    }

    void start(MachineProfile selected, Surface surface) throws IOException {
        start(selected, surface, DynamicCyclePolicy.FIXED_20K);
    }

    void start(MachineProfile selected, Surface surface, int dynamicCycles) throws IOException {
        try {
            start(selected, surface, DynamicCyclePolicy.fromFixedCycles(dynamicCycles));
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    void start(MachineProfile selected, Surface surface, DynamicCyclePolicy dynamicPolicy)
            throws IOException {
        if (attempt != null || finished) throw new IOException("Dynamic trial is already active");
        profile = selected;
        latestSurface = surface;
        attempt = machineStore.prepareDynamicStart(selected, dynamicPolicy);
        startedAtMillis = SystemClock.elapsedRealtime();
        progressWatchdog = new DynamicProgressWatchdog(startedAtMillis);
        try {
            client.connect(() -> {
                try {
                    Surface currentSurface = latestSurface;
                    if (currentSurface == null || !currentSurface.isValid()) {
                        throw new IOException("Dynamic guest surface is unavailable");
                    }
                    client.start(attempt, profile, currentSurface);
                    childStarted = true;
                    if (desiredPaused) client.setPaused(true);
                    handler.postDelayed(livenessPoll, 250);
                } catch (IOException error) {
                    quarantine(error.getMessage());
                }
            });
        } catch (IOException error) {
            quarantine(error.getMessage());
            throw error;
        }
    }

    void setPaused(boolean paused) throws IOException {
        desiredPaused = paused;
        long now = SystemClock.elapsedRealtime();
        if (progressWatchdog != null) progressWatchdog.setPaused(paused, now);
        if (paused && pausedAtMillis < 0) pausedAtMillis = now;
        if (!paused && pausedAtMillis >= 0) {
            pausedMillis += now - pausedAtMillis;
            pausedAtMillis = -1;
        }
        if (childStarted) client.setPaused(paused);
    }
    void setSurface(Surface surface) throws IOException {
        latestSurface = surface;
        if (childStarted) client.setSurface(surface);
    }
    void cancelInput() throws IOException {
        if (childStarted) client.cancelInput();
    }
    void pushKey(int action, int keyCode, int scanCode, int repeatCount, int metaState,
            int source, int device, long eventNanos) throws IOException {
        if (childStarted) {
            client.pushKey(action, keyCode, scanCode, repeatCount, metaState, source, device,
                    eventNanos);
        }
    }
    void pushMouse(int action, float relativeX, float relativeY, float absoluteX, float absoluteY,
            int buttonState, int actionButton, float verticalScroll, float horizontalScroll,
            int source, int device, long eventNanos, boolean captured) throws IOException {
        if (childStarted) {
            client.pushMouse(action, relativeX, relativeY, absoluteX, absoluteY, buttonState,
                    actionButton, verticalScroll, horizontalScroll, source, device, eventNanos,
                    captured);
        }
    }
    void pushTouch(int action, int pointerCount, float x, float y, float pressure,
            int source, int device, long eventNanos) throws IOException {
        if (childStarted) {
            client.pushTouch(action, pointerCount, x, y, pressure, source, device, eventNanos);
        }
    }
    void queryStatus() throws IOException {
        if (childStarted) client.queryStatus();
    }
    void restart() throws IOException {
        if (!childStarted) throw new IOException("Dynamic runner is still starting");
        client.restart();
    }

    int readinessMask() {
        return attempt == null ? 0 : attempt.readinessMask;
    }

    void confirmReadiness(int evidence) throws IOException {
        if (!childStarted || attempt == null || !DynamicAttempt.RUNNING.equals(attempt.state)) {
            throw new IOException("Wait for the dynamic guest to start responding");
        }
        attempt = machineStore.markDynamicReadiness(attempt, evidence);
    }

    /** User Exit is uncertain even when the process appears to stop normally. */
    void stop() {
        if (attempt == null || finished) return;
        try {
            client.requestStop();
        } catch (IOException error) {
            quarantine(error.getMessage());
        }
    }

    void destroy() {
        if (attempt != null && !finished) quarantine("Dynamic runner was destroyed");
        client.disconnect();
        handler.removeCallbacks(livenessPoll);
    }

    @Override public void onDynamicStatus(int status, String error, String liveness) {
        if (attempt == null || finished) return;
        if (error != null || status == NativeHost.SESSION_FAILED) {
            quarantine(error == null ? "Dynamic runner failed" : error);
            return;
        }
        try {
            DynamicLiveness proof = DynamicLiveness.parse(liveness);
            if (progressWatchdog != null) {
                progressWatchdog.observe(SystemClock.elapsedRealtime(), proof.runCalls);
            }
            recordResidency(proof);
            if (status == NativeHost.SESSION_RUNNING &&
                    DynamicAttempt.EXECUTING.equals(attempt.state)) {
                if (proof.provesRunning(unpausedElapsedMillis())) {
                    if (!proof.usesDynRec()) {
                        quarantine("Dynamic runner did not select the ARM64 DynRec decoder");
                        return;
                    }
                    attempt = machineStore.markDynamicRunning(attempt);
                }
            } else if (status == NativeHost.SESSION_GUEST_SHUTDOWN) {
                guestShutdownObserved = true;
                client.requestStop();
            } else if (status == NativeHost.SESSION_STOPPED) {
                if (guestShutdownObserved && DynamicAttempt.RUNNING.equals(attempt.state)) {
                    machineStore.closeDynamicAttemptCleanly(attempt);
                    finished = true;
                    childStarted = false;
                    handler.removeCallbacks(livenessPoll);
                    client.disconnect();
                } else {
                    quarantine("Dynamic trial stopped without guest shutdown");
                    return;
                }
            }
            listener.onStatus(status, null, proof.residencySummary());
        } catch (IOException failure) {
            quarantine(failure.getMessage());
        }
    }

    private void quarantine(String error) {
        if (attempt == null || finished) return;
        try {
            machineStore.quarantineDynamicAttempt(attempt);
        } catch (IOException ignored) {
            // The durable journal remains in place and startup recovery will block it.
        }
        finished = true;
        handler.removeCallbacks(livenessPoll);
        if (childStarted) {
            try {
                client.requestStop();
            } catch (IOException ignored) {
                // Quarantine is already durable; unbinding also destroys the service.
            }
        }
        childStarted = false;
        client.disconnect();
        listener.onStatus(NativeHost.SESSION_FAILED,
                error == null ? "Dynamic trial needs a disk health check" : error, null);
    }

    private void pollLiveness() {
        if (attempt == null || finished) return;
        if (progressWatchdog != null &&
                progressWatchdog.timedOut(SystemClock.elapsedRealtime())) {
            quarantine("Dynamic runner stopped responding");
            return;
        }
        try {
            client.queryStatus();
            handler.postDelayed(livenessPoll, 250);
        } catch (IOException error) {
            quarantine(error.getMessage());
        }
    }

    private long unpausedElapsedMillis() {
        long now = SystemClock.elapsedRealtime();
        long currentPause = pausedAtMillis < 0 ? 0 : now - pausedAtMillis;
        return Math.max(0, now - startedAtMillis - pausedMillis - currentPause);
    }

    private void recordResidency(DynamicLiveness proof) {
        long now = SystemClock.elapsedRealtime();
        if (now - residencyLoggedAtMillis < 5_000) return;
        residencyLoggedAtMillis = now;
        android.util.Log.i("RoboWindowsDynrec", "residency " + proof.residencySummary());
    }
}
