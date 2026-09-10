package org.robowindows.app;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The only process permitted to load a dynamic-core launch. It intentionally
 * exposes no normal-core command, so a normal guest can never reuse this
 * process-global native core state.
 */
public final class DynamicTrialService extends Service {
    private Messenger client;
    private boolean started;
    private boolean processExitScheduled;

    private final Messenger messenger = new Messenger(new Handler(message -> {
        handle(message);
        return true;
    }));

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override public void onDestroy() {
        if (started) NativeHost.stopSession();
        started = false;
        super.onDestroy();
        scheduleProcessExit();
    }

    private void handle(Message message) {
        if (message.replyTo != null) client = message.replyTo;
        Bundle data = message.getData();
        if (data != null) data.setClassLoader(Surface.class.getClassLoader());
        try {
            switch (message.what) {
                case DynamicTrialProtocol.START:
                    start(data);
                    break;
                case DynamicTrialProtocol.STOP:
                    stopSelfAfterNativeStop();
                    break;
                case DynamicTrialProtocol.PAUSE:
                    requireStarted();
                    NativeHost.setPaused(data.getBoolean(DynamicTrialProtocol.PAUSED));
                    break;
                case DynamicTrialProtocol.SURFACE:
                    requireStarted();
                    NativeHost.setSurface(data.getParcelable(DynamicTrialProtocol.SURFACE_VALUE));
                    break;
                case DynamicTrialProtocol.CANCEL_INPUT:
                    requireStarted();
                    NativeHost.cancelInput();
                    break;
                case DynamicTrialProtocol.KEY:
                    requireStarted();
                    NativeHost.pushKey(data.getInt(DynamicTrialProtocol.ACTION),
                            data.getInt(DynamicTrialProtocol.KEY_CODE),
                            data.getInt(DynamicTrialProtocol.SCAN_CODE),
                            data.getInt(DynamicTrialProtocol.REPEAT_COUNT),
                            data.getInt(DynamicTrialProtocol.META_STATE),
                            data.getInt(DynamicTrialProtocol.SOURCE),
                            data.getInt(DynamicTrialProtocol.DEVICE),
                            data.getLong(DynamicTrialProtocol.EVENT_NANOS));
                    break;
                case DynamicTrialProtocol.MOUSE:
                    requireStarted();
                    NativeHost.pushMouse(data.getInt(DynamicTrialProtocol.ACTION),
                            data.getFloat(DynamicTrialProtocol.RELATIVE_X),
                            data.getFloat(DynamicTrialProtocol.RELATIVE_Y),
                            data.getFloat(DynamicTrialProtocol.ABSOLUTE_X),
                            data.getFloat(DynamicTrialProtocol.ABSOLUTE_Y),
                            data.getInt(DynamicTrialProtocol.BUTTON_STATE),
                            data.getInt(DynamicTrialProtocol.ACTION_BUTTON),
                            data.getFloat(DynamicTrialProtocol.V_SCROLL),
                            data.getFloat(DynamicTrialProtocol.H_SCROLL),
                            data.getInt(DynamicTrialProtocol.SOURCE),
                            data.getInt(DynamicTrialProtocol.DEVICE),
                            data.getLong(DynamicTrialProtocol.EVENT_NANOS),
                            data.getBoolean(DynamicTrialProtocol.CAPTURED));
                    break;
                case DynamicTrialProtocol.TOUCH:
                    requireStarted();
                    NativeHost.pushTouch(data.getInt(DynamicTrialProtocol.ACTION),
                            data.getInt(DynamicTrialProtocol.POINTER_COUNT),
                            data.getFloat(DynamicTrialProtocol.ABSOLUTE_X),
                            data.getFloat(DynamicTrialProtocol.ABSOLUTE_Y),
                            data.getFloat(DynamicTrialProtocol.PRESSURE),
                            data.getInt(DynamicTrialProtocol.SOURCE),
                            data.getInt(DynamicTrialProtocol.DEVICE),
                            data.getLong(DynamicTrialProtocol.EVENT_NANOS));
                    break;
                case DynamicTrialProtocol.STATUS:
                    sendStatus(null);
                    break;
                case DynamicTrialProtocol.RESTART:
                    requireStarted();
                    if (!NativeHost.restartSession()) {
                        throw new IOException("Dynamic runner could not restart the active trial");
                    }
                    break;
                default:
                    throw new IOException("Unsupported dynamic runner command");
            }
            if (message.what != DynamicTrialProtocol.STATUS) sendStatus(null);
        } catch (IOException | RuntimeException error) {
            sendStatus(error.getMessage() == null ? "Dynamic runner failed" : error.getMessage());
        }
    }

    private void start(Bundle data) throws IOException {
        if (!CpuFixtureGate.passed(this)) {
            throw new IOException("DynRec requires a passed CPU test for this build");
        }
        if (started) throw new IOException("Dynamic runner already owns a session");
        if (data == null) throw new IOException("Dynamic launch description is missing");
        String dynamicPolicyId = data.getString(DynamicTrialProtocol.DYNAMIC_CYCLE_POLICY);
        int timingPolicy = data.getInt(DynamicTrialProtocol.RUNTIME_TIMING_POLICY, -1);
        if (!RuntimeTimingPolicy.isAllowed(timingPolicy) ||
                timingPolicy != RuntimeTimingPolicy.BALANCED_100_MS) {
            throw new IOException("Dynamic runner rejects the runtime timing policy");
        }
        DynamicCyclePolicy dynamicPolicy;
        try {
            dynamicPolicy = DynamicCyclePolicy.fromId(dynamicPolicyId);
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }
        MachineProfile profile = new MachineStore(this).validateDynamicChildHandoff(
                data.getString(DynamicTrialProtocol.MACHINE_ID),
                data.getString(DynamicTrialProtocol.ATTEMPT_ID),
                data.getLong(DynamicTrialProtocol.GENERATION), dynamicPolicy.id);
        File launch = validatedDynamicLaunch(profile, data.getString(DynamicTrialProtocol.LAUNCH_PATH),
                data.getString(DynamicTrialProtocol.FILES_PATH), dynamicPolicy);
        int presentation = data.getInt(DynamicTrialProtocol.PRESENTATION_POLICY, -1);
        if (!PresentationPolicy.allowed(presentation) || presentation != PresentationPolicy.forLaunch(
                profile.presentationMode, BuildConfig.DEBUG, profile.isExperimental(), false)) {
            throw new IOException("Dynamic runner rejects the presentation policy");
        }
        Surface surface = data.getParcelable(DynamicTrialProtocol.SURFACE_VALUE);
        NativeHost.setSurface(surface);
        if (!NativeHost.startSession(launch.getPath(), getFilesDir().getPath(), timingPolicy, presentation)) {
            throw new IOException("Dynamic native start failed");
        }
        started = true;
    }

    private File validatedDynamicLaunch(MachineProfile profile, String launchPath, String filesPath,
            DynamicCyclePolicy dynamicPolicy) throws IOException {
        if (!getFilesDir().getCanonicalPath().equals(new File(filesPath).getCanonicalPath())) {
            throw new IOException("Dynamic runner storage does not match this app");
        }
        File root = new File(getFilesDir(), "machines").getCanonicalFile();
        File launch = new File(launchPath).getCanonicalFile();
        if (!launch.equals(new File(profile.launchPath).getCanonicalFile()) || !launch.isFile() ||
                !"launch.conf".equals(launch.getName()) ||
                !launch.getParentFile().getParentFile().equals(root)) {
            throw new IOException("Dynamic launch is outside machine storage");
        }
        byte[] bytes = Files.readAllBytes(launch.toPath());
        if (bytes.length > 64 * 1024) throw new IOException("Dynamic launch is too large");
        String config = new String(bytes, StandardCharsets.UTF_8);
        String expectedCore = "\ncore=dynamic\n";
        String expectedCycles = "\ncycles=" + dynamicPolicy.configValue + "\n";
        if (!config.contains(expectedCore) || !config.contains(expectedCycles) ||
                occurrences(config, "\ncore=") != 1 || occurrences(config, "\ncycles=") != 1) {
            throw new IOException("Dynamic runner rejects a non-dynamic launch");
        }
        return launch;
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(needle, offset)) >= 0;
                offset += needle.length()) count++;
        return count;
    }

    private void requireStarted() throws IOException {
        if (!started) throw new IOException("Dynamic runner has no active session");
    }

    private void stopSelfAfterNativeStop() {
        if (started) NativeHost.stopSession();
        started = false;
        stopSelf();
        scheduleProcessExit();
    }

    private void scheduleProcessExit() {
        if (processExitScheduled) return;
        processExitScheduled = true;
        IsolatedProcessExit.afterResult();
    }

    private void sendStatus(String error) {
        if (client == null) return;
        Message reply = Message.obtain();
        reply.what = DynamicTrialProtocol.STATUS;
        Bundle data = new Bundle();
        data.putInt(DynamicTrialProtocol.STATUS_VALUE,
                started ? NativeHost.sessionStatus() : NativeHost.SESSION_STOPPED);
        if (started) data.putString(DynamicTrialProtocol.LIVENESS, NativeHost.sessionLiveness());
        if (started) data.putInt(DynamicTrialProtocol.PRESENTATION_STATUS, NativeHost.sessionPresentation());
        if (error != null) data.putString(DynamicTrialProtocol.ERROR, error);
        reply.setData(data);
        try {
            client.send(reply);
        } catch (RemoteException ignored) {
            client = null;
        }
    }
}
