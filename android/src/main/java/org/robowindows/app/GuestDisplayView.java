package org.robowindows.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Surface;
import android.view.MotionEvent;
import java.util.function.IntUnaryOperator;

final class GuestDisplayView extends SurfaceView implements SurfaceHolder.Callback {
    interface SessionBridge {
        void setSurface(Surface surface);
        void pushMouse(MotionEvent event, int deviceHandle, boolean captured);
    }

    private final IntUnaryOperator deviceHandle;
    private final SessionBridge bridge;
    private final boolean drawWaitingFrame;

    GuestDisplayView(Context context, IntUnaryOperator deviceHandle) {
        this(context, deviceHandle, true);
    }

    GuestDisplayView(Context context, IntUnaryOperator deviceHandle, boolean drawWaitingFrame) {
        this(context, deviceHandle, new SessionBridge() {
            @Override public void setSurface(Surface surface) { NativeHost.setSurface(surface); }
            @Override public void pushMouse(MotionEvent event, int handle, boolean captured) {
                NativeHost.pushMouse(event.getActionMasked(),
                        event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                        event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y), event.getX(), event.getY(),
                        event.getButtonState(), event.getActionButton(),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getSource(), handle,
                        event.getEventTime() * 1_000_000L, captured);
            }
        }, drawWaitingFrame);
    }

    GuestDisplayView(Context context, IntUnaryOperator deviceHandle, SessionBridge bridge) {
        this(context, deviceHandle, bridge, true);
    }

    GuestDisplayView(Context context, IntUnaryOperator deviceHandle, SessionBridge bridge,
            boolean drawWaitingFrame) {
        super(context);
        this.deviceHandle = deviceHandle;
        this.bridge = bridge;
        this.drawWaitingFrame = drawWaitingFrame;
        setContentDescription("Guest display");
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private void drawWaitingFrame() {
        Canvas canvas = getHolder().lockCanvas();
        if (canvas == null) return;
        try {
            canvas.drawColor(Color.rgb(8, 10, 13));
        } finally {
            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    @Override public boolean onCapturedPointerEvent(MotionEvent event) {
        bridge.pushMouse(event, deviceHandle.applyAsInt(event.getDeviceId()), true);
        return true;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        bridge.setSurface(holder.getSurface());
        if (drawWaitingFrame) drawWaitingFrame();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        bridge.setSurface(holder.getSurface());
        if (drawWaitingFrame) drawWaitingFrame();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { bridge.setSurface(null); }
}
