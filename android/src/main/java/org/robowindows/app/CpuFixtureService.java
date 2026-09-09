package org.robowindows.app;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.io.IOException;

/** Base for the two non-exported, machine-independent CPU fixture processes. */
abstract class CpuFixtureService extends Service {
    private static final long EXECUTION_MILLIS = 3_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Messenger client;
    private CpuFixtureFiles.Run run;
    private boolean started;
    private boolean terminating;
    private ExpandedCpuSuite suite;

    protected abstract String mode();

    private final Messenger messenger = new Messenger(new Handler(message -> {
        if (message.replyTo != null) client = message.replyTo;
        if (message.what != CpuFixtureProtocol.START) {
            finish("Unsupported CPU fixture command", null);
        } else if (started) {
            finish("CPU fixture is already running", null);
        } else {
            int suiteId = message.getData().getInt(CpuFixtureProtocol.SUITE_ID,
                    CpuFixtureProtocol.LEGACY_SUITE_ID);
            if (suiteId != CpuFixtureProtocol.LEGACY_SUITE_ID) {
                suite = ExpandedCpuSuite.fromId(suiteId);
                if (suite == null) {
                    finish("CPU fixture suite is unsupported", null);
                    return true;
                }
            }
            startFixture();
        }
        return true;
    }));

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override public void onDestroy() {
        boolean needsProcessExit = !terminating;
        if (!terminating) handler.removeCallbacksAndMessages(null);
        if (started) NativeHost.stopSession();
        started = false;
        CpuFixtureFiles.remove(run);
        super.onDestroy();
        if (needsProcessExit) IsolatedProcessExit.afterResult();
    }

    private void startFixture() {
        try {
            run = CpuFixtureFiles.create(this, mode(), suite);
            NativeHost.setSurface(null);
            if (!NativeHost.startSession(run.launch.getPath(), getFilesDir().getPath(),
                    RuntimeTimingPolicy.LEGACY)) {
                throw new IOException("CPU fixture native start failed");
            }
            started = true;
            handler.postDelayed(this::collectResult, EXECUTION_MILLIS);
        } catch (IOException | RuntimeException error) {
            finish(message(error), null);
        }
    }

    private void collectResult() {
        byte[] record = null;
        String error = null;
        String liveness = null;
        try {
            String decoder = NativeHost.sessionDecoder();
            liveness = NativeHost.sessionLiveness();
            if (started) NativeHost.stopSession();
            started = false;
            String expectedDecoder = "dynamic".equals(mode()) ? "DynRec" : "Normal";
            if (!expectedDecoder.equals(decoder)) {
                throw new IOException("CPU fixture selected " + decoder +
                        " instead of " + expectedDecoder);
            }
            record = CpuFixtureFiles.readResult(run);
        } catch (IOException | RuntimeException failure) {
            error = message(failure);
            if (liveness != null) {
                error += " · " + DynamicLiveness.parse(liveness).residencySummary();
            }
        }
        finish(error, record);
    }

    private void finish(String error, byte[] record) {
        if (started) NativeHost.stopSession();
        started = false;
        Message reply = Message.obtain();
        reply.what = CpuFixtureProtocol.RESULT;
        Bundle data = new Bundle();
        data.putString(CpuFixtureProtocol.MODE, mode());
        data.putInt(CpuFixtureProtocol.SUITE_ID,
                suite == null ? CpuFixtureProtocol.LEGACY_SUITE_ID : suite.id);
        if (error != null) data.putString(CpuFixtureProtocol.ERROR, error);
        if (record != null) data.putByteArray(CpuFixtureProtocol.RECORD, record);
        reply.setData(data);
        try {
            if (client != null) client.send(reply);
        } catch (RemoteException ignored) {
            // The host timeout treats a lost reply as failure.
        }
        CpuFixtureFiles.remove(run);
        run = null;
        terminating = true;
        stopSelf();
        IsolatedProcessExit.afterResult();
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? "CPU fixture failed" : error.getMessage();
    }
}
