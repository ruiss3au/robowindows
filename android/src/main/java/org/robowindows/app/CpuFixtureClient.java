package org.robowindows.app;

import android.app.Service;
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

import java.io.IOException;

/** One-shot binder client for one isolated CPU fixture process. */
final class CpuFixtureClient {
    interface Listener {
        void onResult(String mode, int suiteId, byte[] record, String error);
        void onDisconnected();
    }

    private final Context context;
    private final Class<? extends Service> serviceClass;
    private final Listener listener;
    private final int suiteId;
    private final Messenger replies;
    private Messenger service;
    private boolean bound;
    private boolean finished;

    CpuFixtureClient(Context context, Class<? extends Service> serviceClass, int suiteId,
            Listener listener) {
        this.context = context.getApplicationContext();
        this.serviceClass = serviceClass;
        this.listener = listener;
        this.suiteId = suiteId;
        replies = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            if (message.what == CpuFixtureProtocol.RESULT && !finished) {
                finished = true;
                Bundle data = message.getData();
                listener.onResult(data.getString(CpuFixtureProtocol.MODE),
                        data.getInt(CpuFixtureProtocol.SUITE_ID,
                                CpuFixtureProtocol.LEGACY_SUITE_ID),
                        data.getByteArray(CpuFixtureProtocol.RECORD),
                        data.getString(CpuFixtureProtocol.ERROR));
            }
            return true;
        }));
    }

    void start() throws IOException {
        if (bound) throw new IOException("CPU fixture client is already connected");
        bound = context.bindService(new Intent(context, serviceClass), connection,
                Context.BIND_AUTO_CREATE);
        if (!bound) throw new IOException("Cannot bind the CPU fixture runner");
    }

    void disconnect() {
        if (bound) context.unbindService(connection);
        bound = false;
        service = null;
    }

    private void sendStart() throws IOException {
        if (service == null) throw new IOException("CPU fixture runner is not connected");
        Message message = Message.obtain();
        message.what = CpuFixtureProtocol.START;
        message.replyTo = replies;
        Bundle data = new Bundle();
        data.putInt(CpuFixtureProtocol.SUITE_ID, suiteId);
        message.setData(data);
        try {
            service.send(message);
        } catch (RemoteException error) {
            throw new IOException("CPU fixture runner disconnected", error);
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = new Messenger(binder);
            try {
                sendStart();
            } catch (IOException error) {
                if (!finished) {
                    finished = true;
                    listener.onResult(null, CpuFixtureProtocol.LEGACY_SUITE_ID,
                            null, error.getMessage());
                }
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            if (!finished) listener.onDisconnected();
        }

        @Override public void onBindingDied(ComponentName name) {
            service = null;
            if (!finished) listener.onDisconnected();
        }
    };
}
