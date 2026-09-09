package org.robowindows.app;

import android.app.Activity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.util.Log;

/** Disposable surface only: never boots or opens guest media. */
final class GraphicsProbe {
    static void show(Activity activity) {
        MachineStore store = new MachineStore(activity);
        if (!BuildConfig.DEBUG || store.hasInterruptedSession() || NativeHost.sessionStatus() != NativeHost.SESSION_STOPPED) {
            Log.e("RoboWindowsGraphics", "FAIL session is not stopped"); return;
        }
        SurfaceView surface = new SurfaceView(activity);
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            private boolean started;
            @Override public void surfaceCreated(SurfaceHolder holder) {}
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (started) return;
                started = true;
                new Thread(() -> {
                    String result = NativeHost.runGraphicsProbe(holder.getSurface());
                    Log.i("RoboWindowsGraphics", result);
                    activity.runOnUiThread(activity::finish);
                }, "graphics-probe").start();
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        activity.setContentView(surface);
    }
}
