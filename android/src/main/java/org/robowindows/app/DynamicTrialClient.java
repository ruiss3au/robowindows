package org.robowindows.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Surface;

import java.io.IOException;

/** Host-process proxy for the one isolated dynamic-core emulator process. */
final class DynamicTrialClient {
    interface Listener {
        void onDynamicStatus(int status, String error);
    }

    private final Context context;
    private final Listener listener;
    private final Messenger replies;
    private Messenger service;
    private Runnable whenConnected;
    private boolean bound;

    DynamicTrialClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        replies = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            if (message.what == DynamicTrialProtocol.STATUS) {
                Bundle data = message.getData();
                listener.onDynamicStatus(data.getInt(DynamicTrialProtocol.STATUS_VALUE,
                        NativeHost.SESSION_STOPPED), data.getString(DynamicTrialProtocol.ERROR));
            }
            return true;
        }));
    }

    void connect(Runnable onConnected) throws IOException {
        if (service != null) {
            onConnected.run();
            return;
        }
        whenConnected = onConnected;
        Intent intent = new Intent(context, DynamicTrialService.class);
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) throw new IOException("Cannot bind the dynamic runner");
    }

    void start(DynamicAttempt attempt, MachineProfile profile, Surface surface) throws IOException {
        Bundle data = new Bundle();
        data.putString(DynamicTrialProtocol.MACHINE_ID, attempt.machineId);
        data.putString(DynamicTrialProtocol.ATTEMPT_ID, attempt.attemptId);
        data.putLong(DynamicTrialProtocol.GENERATION, attempt.generation);
        data.putString(DynamicTrialProtocol.LAUNCH_PATH, profile.launchPath);
        data.putString(DynamicTrialProtocol.FILES_PATH, context.getFilesDir().getPath());
        data.putParcelable(DynamicTrialProtocol.SURFACE_VALUE, surface);
        send(DynamicTrialProtocol.START, data);
    }

    void requestStop() throws IOException {
        if (service != null) {
            send(DynamicTrialProtocol.STOP, new Bundle());
        }
    }

    void disconnect() {
        if (bound) context.unbindService(connection);
        bound = false;
        service = null;
        whenConnected = null;
    }

    void setPaused(boolean paused) throws IOException {
        Bundle data = new Bundle();
        data.putBoolean(DynamicTrialProtocol.PAUSED, paused);
        send(DynamicTrialProtocol.PAUSE, data);
    }

    void setSurface(Surface surface) throws IOException {
        Bundle data = new Bundle();
        data.putParcelable(DynamicTrialProtocol.SURFACE_VALUE, surface);
        send(DynamicTrialProtocol.SURFACE, data);
    }

    void cancelInput() throws IOException {
        send(DynamicTrialProtocol.CANCEL_INPUT, new Bundle());
    }

    void pushKey(int action, int keyCode, int scanCode, int repeatCount, int metaState,
            int source, int device, long eventNanos) throws IOException {
        Bundle data = baseInput(action, source, device, eventNanos);
        data.putInt(DynamicTrialProtocol.KEY_CODE, keyCode);
        data.putInt(DynamicTrialProtocol.SCAN_CODE, scanCode);
        data.putInt(DynamicTrialProtocol.REPEAT_COUNT, repeatCount);
        data.putInt(DynamicTrialProtocol.META_STATE, metaState);
        send(DynamicTrialProtocol.KEY, data);
    }

    void pushMouse(int action, float relativeX, float relativeY, float absoluteX,
            float absoluteY, int buttonState, int actionButton, float verticalScroll,
            float horizontalScroll, int source, int device, long eventNanos, boolean captured)
            throws IOException {
        Bundle data = baseInput(action, source, device, eventNanos);
        data.putFloat(DynamicTrialProtocol.RELATIVE_X, relativeX);
        data.putFloat(DynamicTrialProtocol.RELATIVE_Y, relativeY);
        data.putFloat(DynamicTrialProtocol.ABSOLUTE_X, absoluteX);
        data.putFloat(DynamicTrialProtocol.ABSOLUTE_Y, absoluteY);
        data.putInt(DynamicTrialProtocol.BUTTON_STATE, buttonState);
        data.putInt(DynamicTrialProtocol.ACTION_BUTTON, actionButton);
        data.putFloat(DynamicTrialProtocol.V_SCROLL, verticalScroll);
        data.putFloat(DynamicTrialProtocol.H_SCROLL, horizontalScroll);
        data.putBoolean(DynamicTrialProtocol.CAPTURED, captured);
        send(DynamicTrialProtocol.MOUSE, data);
    }

    void pushTouch(int action, int pointerCount, float x, float y, float pressure,
            int source, int device, long eventNanos) throws IOException {
        Bundle data = baseInput(action, source, device, eventNanos);
        data.putInt(DynamicTrialProtocol.POINTER_COUNT, pointerCount);
        data.putFloat(DynamicTrialProtocol.ABSOLUTE_X, x);
        data.putFloat(DynamicTrialProtocol.ABSOLUTE_Y, y);
        data.putFloat(DynamicTrialProtocol.PRESSURE, pressure);
        send(DynamicTrialProtocol.TOUCH, data);
    }

    void queryStatus() throws IOException {
        send(DynamicTrialProtocol.STATUS, new Bundle());
    }

    private void send(int what, Bundle data) throws IOException {
        if (service == null) throw new IOException("Dynamic runner is not connected");
        Message message = Message.obtain();
        message.what = what;
        message.setData(data);
        message.replyTo = replies;
        try {
            service.send(message);
        } catch (RemoteException error) {
            service = null;
            listener.onDynamicStatus(NativeHost.SESSION_FAILED, "Dynamic runner disconnected");
            throw new IOException("Dynamic runner disconnected", error);
        }
    }

    private static Bundle baseInput(int action, int source, int device, long eventNanos) {
        Bundle data = new Bundle();
        data.putInt(DynamicTrialProtocol.ACTION, action);
        data.putInt(DynamicTrialProtocol.SOURCE, source);
        data.putInt(DynamicTrialProtocol.DEVICE, device);
        data.putLong(DynamicTrialProtocol.EVENT_NANOS, eventNanos);
        return data;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = new Messenger(binder);
            Runnable callback = whenConnected;
            whenConnected = null;
            if (callback != null) callback.run();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            listener.onDynamicStatus(NativeHost.SESSION_FAILED, "Dynamic runner disconnected");
        }

        @Override public void onBindingDied(ComponentName name) {
            service = null;
            listener.onDynamicStatus(NativeHost.SESSION_FAILED, "Dynamic runner process ended");
        }
    };
}
