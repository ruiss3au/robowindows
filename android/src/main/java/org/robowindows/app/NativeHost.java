package org.robowindows.app;

import android.view.Surface;

final class NativeHost {
    static final int SESSION_STOPPED = 0;
    static final int SESSION_STARTING = 1;
    static final int SESSION_RUNNING = 2;
    static final int SESSION_FAILED = 3;
    static final int SESSION_GUEST_SHUTDOWN = 4;

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
    static native String sessionLiveness();
    static native String sessionDecoder();
    static native boolean restartSession();
    static native void setPaused(boolean paused);
    static native void stopSession();
    static native void setSurface(Surface surface);
    static native boolean changeMedia(String mediaPath);
}
