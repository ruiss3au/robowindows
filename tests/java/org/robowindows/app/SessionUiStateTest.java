package org.robowindows.app;

public final class SessionUiStateTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        startupAndTimeout();
        captureSuccessAndNoDuplicateTransition();
        captureFailure();
        revealActions();
        pauseResumeAndExit();
        System.out.println("Session UI state checks passed");
    }

    private static void startupAndTimeout() {
        SessionUiState state = new SessionUiState();
        state.start(100);
        require(state.areControlsVisible(), "controls must start visible");
        state.update(4_099);
        require(state.areControlsVisible(), "controls hid before four seconds");
        state.update(4_100);
        require(!state.areControlsVisible(), "controls did not hide at four seconds");
    }

    private static void captureSuccessAndNoDuplicateTransition() {
        SessionUiState state = new SessionUiState();
        state.start(0);
        require(state.requestCapture(10), "first guest click did not request capture");
        require(!state.requestCapture(11), "capture transition accepted a duplicate click");
        require(state.isCapturePending(), "capture request was not pending");
        require(!state.pointerCaptureChanged(true, 20), "successful capture reported failure");
        require(state.isCaptured(), "capture callback did not enter captured state");
        require(!state.areControlsVisible(), "capture success did not hide controls");
    }

    private static void captureFailure() {
        SessionUiState callbackFailure = new SessionUiState();
        callbackFailure.start(0);
        callbackFailure.requestCapture(10);
        require(callbackFailure.pointerCaptureChanged(false, 20), "failure callback not reported");
        require(callbackFailure.didCaptureFail() && callbackFailure.areControlsVisible(),
                "failure did not retain controls and error state");
        callbackFailure.update(20_000);
        require(callbackFailure.areControlsVisible(), "failed capture controls auto-hid");
        require(callbackFailure.requestCapture(20_001), "retry after capture failure was rejected");

        SessionUiState timeout = new SessionUiState();
        timeout.start(0);
        timeout.requestCapture(10);
        require(!timeout.captureRequestTimedOut(759), "capture timed out too early");
        require(timeout.captureRequestTimedOut(760), "capture timeout not detected");
        require(timeout.didCaptureFail() && timeout.areControlsVisible(),
                "timeout did not retain controls and error state");
    }

    private static void revealActions() {
        SessionUiState state = new SessionUiState();
        state.start(0);
        state.update(4_000);
        require(state.revealFromUser(5_000), "Back/top edge did not reveal hidden controls");
        require(state.areControlsVisible() && !state.isCaptured(), "reveal state is incorrect");
        state.requestCapture(5_100);
        state.pointerCaptureChanged(true, 5_110);
        require(state.revealFromUser(5_200), "Back/top edge did not release capture");
        require(!state.isCaptured() && state.areControlsVisible(), "capture remained after reveal");
        require(!state.revealFromUser(5_300), "visible released controls consumed Back");
    }

    private static void pauseResumeAndExit() {
        SessionUiState state = new SessionUiState();
        state.start(0);
        state.requestCapture(10);
        state.pointerCaptureChanged(true, 20);
        state.pause(30);
        require(!state.isCaptured() && state.areControlsVisible(), "pause did not release and show");
        state.update(10_000);
        require(state.areControlsVisible(), "paused controls auto-hid");
        state.resume(11_000);
        state.update(14_999);
        require(state.areControlsVisible(), "resumed controls hid early");
        state.update(15_000);
        require(!state.areControlsVisible(), "resumed controls did not time out");
        state.exit();
        require(!state.isRunning() && !state.isCaptured() && !state.areControlsVisible(),
                "exit did not clean state");
    }
}
