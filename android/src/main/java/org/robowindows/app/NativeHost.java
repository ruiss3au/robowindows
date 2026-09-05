package org.robowindows.app;

import android.view.Surface;

final class NativeHost {
    static {
        System.loadLibrary("robowindows_host");
    }

    private NativeHost() {}

    static native void pushKey(int action, int keyCode, int scanCode, int repeatCount,
            int metaState, int source, int deviceHandle, long eventTimeNanos);
    static native void pushMouse(int action, float relativeX, float relativeY,
            float absoluteX, float absoluteY, int buttonState, int actionButton,
            float verticalScroll, float horizontalScroll, int source, int deviceHandle,
            long eventTimeNanos, boolean captured);
    static native void pushTouch(int action, int pointerCount, float x, float y,
            float pressure, int source, int deviceHandle, long eventTimeNanos);
    static native void cancelInput();
    static native String inputStats();
    static native String lastInputEvent();
    static native boolean startSession(String contentPath, String filesPath);
    static native int sessionStatus();
    static native boolean restartSession();
    static native void setPaused(boolean paused);
    static native void stopSession();
    static native void setSurface(Surface surface);
    static native boolean changeMedia(String mediaPath);
}
