package org.robowindows.app;

import android.content.Context;
import android.view.Surface;

import java.io.IOException;

/** Internal owner of an isolated dynamic trial and its durable journal. */
final class DynamicTrialController implements DynamicTrialClient.Listener {
    interface Listener {
        void onStatus(int status, String error);
    }

    private final MachineStore machineStore;
    private final DynamicTrialClient client;
    private final Listener listener;
    private DynamicAttempt attempt;
    private MachineProfile profile;
    private boolean guestShutdownObserved;
    private boolean finished;

    DynamicTrialController(Context context, Listener listener) {
        machineStore = new MachineStore(context);
        client = new DynamicTrialClient(context, this);
        this.listener = listener;
    }

    void start(MachineProfile selected, Surface surface) throws IOException {
        if (attempt != null || finished) throw new IOException("Dynamic trial is already active");
        profile = selected;
        attempt = machineStore.prepareDynamicStart(selected);
        try {
            client.connect(() -> {
                try {
                    client.start(attempt, profile, surface);
                } catch (IOException error) {
                    quarantine(error.getMessage());
                }
            });
        } catch (IOException error) {
            quarantine(error.getMessage());
            throw error;
        }
    }

    void setPaused(boolean paused) throws IOException { client.setPaused(paused); }
    void setSurface(Surface surface) throws IOException { client.setSurface(surface); }
    void cancelInput() throws IOException { client.cancelInput(); }
    void queryStatus() throws IOException { client.queryStatus(); }

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
    }

    @Override public void onDynamicStatus(int status, String error) {
        if (attempt == null || finished) return;
        if (error != null || status == NativeHost.SESSION_FAILED) {
            quarantine(error == null ? "Dynamic runner failed" : error);
            return;
        }
        try {
            if (status == NativeHost.SESSION_RUNNING &&
                    DynamicAttempt.EXECUTING.equals(attempt.state)) {
                attempt = machineStore.markDynamicRunning(attempt);
            } else if (status == NativeHost.SESSION_GUEST_SHUTDOWN) {
                guestShutdownObserved = true;
                client.requestStop();
            } else if (status == NativeHost.SESSION_STOPPED) {
                if (guestShutdownObserved && DynamicAttempt.RUNNING.equals(attempt.state)) {
                    machineStore.closeDynamicAttemptCleanly(attempt);
                    finished = true;
                    client.disconnect();
                } else {
                    quarantine("Dynamic trial stopped without guest shutdown");
                    return;
                }
            }
            listener.onStatus(status, null);
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
        client.disconnect();
        listener.onStatus(NativeHost.SESSION_FAILED,
                error == null ? "Dynamic trial needs a disk health check" : error);
    }
}
