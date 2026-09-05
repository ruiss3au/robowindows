package org.robowindows.app;

final class SessionUiState {
    static final long CONTROLS_VISIBLE_MS = 4_000L;
    static final long CAPTURE_REQUEST_TIMEOUT_MS = 750L;

    enum CaptureState { RELEASED, REQUESTED, CAPTURED }

    private boolean running;
    private boolean paused;
    private boolean controlsVisible;
    private boolean captureFailed;
    private long hideControlsAt;
    private long captureRequestedAt;
    private CaptureState captureState = CaptureState.RELEASED;

    void start(long now) {
        running = true;
        paused = false;
        captureFailed = false;
        captureState = CaptureState.RELEASED;
        showForTimeout(now);
    }

    void update(long now) {
        if (running && !paused && controlsVisible && !captureFailed &&
                captureState == CaptureState.RELEASED && now >= hideControlsAt) {
            controlsVisible = false;
        }
    }

    boolean requestCapture(long now) {
        if (!running || paused || captureState != CaptureState.RELEASED) return false;
        captureFailed = false;
        captureState = CaptureState.REQUESTED;
        captureRequestedAt = now;
        controlsVisible = true;
        return true;
    }

    boolean captureRequestTimedOut(long now) {
        if (captureState != CaptureState.REQUESTED ||
                now - captureRequestedAt < CAPTURE_REQUEST_TIMEOUT_MS) return false;
        captureState = CaptureState.RELEASED;
        captureFailed = true;
        controlsVisible = true;
        return true;
    }

    boolean pointerCaptureChanged(boolean captured, long now) {
        if (captured) {
            if (!running || paused || captureState != CaptureState.REQUESTED) return false;
            captureState = CaptureState.CAPTURED;
            captureFailed = false;
            controlsVisible = false;
            return false;
        }
        boolean failedRequest = captureState == CaptureState.REQUESTED;
        if (captureState != CaptureState.RELEASED) {
            captureState = CaptureState.RELEASED;
            controlsVisible = true;
            captureFailed = failedRequest;
            hideControlsAt = failedRequest ? Long.MAX_VALUE : now + CONTROLS_VISIBLE_MS;
        }
        return failedRequest;
    }

    boolean revealFromUser(long now) {
        if (!running || (captureState == CaptureState.RELEASED && controlsVisible)) return false;
        releaseAndShow(now);
        return true;
    }

    void releaseAndShow(long now) {
        captureState = CaptureState.RELEASED;
        captureFailed = false;
        if (running) showForTimeout(now);
    }

    void pause(long now) {
        if (!running) return;
        paused = true;
        captureState = CaptureState.RELEASED;
        captureFailed = false;
        controlsVisible = true;
        hideControlsAt = Long.MAX_VALUE;
    }

    void resume(long now) {
        if (!running) return;
        paused = false;
        captureState = CaptureState.RELEASED;
        captureFailed = false;
        showForTimeout(now);
    }

    void exit() {
        running = false;
        paused = false;
        controlsVisible = false;
        captureFailed = false;
        captureState = CaptureState.RELEASED;
    }

    boolean isRunning() { return running; }
    boolean areControlsVisible() { return controlsVisible; }
    boolean isCapturePending() { return captureState == CaptureState.REQUESTED; }
    boolean isCaptured() { return captureState == CaptureState.CAPTURED; }
    boolean didCaptureFail() { return captureFailed; }

    private void showForTimeout(long now) {
        controlsVisible = true;
        hideControlsAt = now + CONTROLS_VISIBLE_MS;
    }
}
