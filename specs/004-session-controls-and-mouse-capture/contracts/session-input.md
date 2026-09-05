# Session Input Contract

## Ownership

During a running session, `GuestDisplayView.onCapturedPointerEvent` is the sole
producer of guest mouse events. It forwards Android `AXIS_RELATIVE_X` and
`AXIS_RELATIVE_Y`, current button state, action button, `AXIS_VSCROLL`, and
`AXIS_HSCROLL` without deriving deltas from cursor position or applying a
sensitivity multiplier.

`MainActivity.dispatchGenericMotionEvent` may observe uncaptured physical-mouse
events only to recognize the first button press inside the guest and request
capture. That event is consumed as capture-only. Motion, wheel, and toolbar
events while uncaptured are not guest input.

## Capture state

`RELEASED -> REQUESTED -> CAPTURED` requires an explicit physical-mouse click in
the guest followed by Android capture confirmation. A failed callback or request
timeout returns to `RELEASED`, leaves controls visible, and presents a
RoboWindows error. Capture success hides controls immediately.

An explicit reveal, pause, focus loss, device removal, exit, or destruction
transitions to `RELEASED`, calls Android pointer release when needed, and emits
native input cancellation. Resume/focus return shows controls and starts a new
four-second visibility window; it never requests capture.

## Reveal controls

A touchscreen `ACTION_DOWN` at activity coordinate `y <= 32 dp` or Android Back
while captured/hidden performs release-and-reveal. Keyboard Escape is forwarded
through the existing keyboard bridge and is not a host gesture.

## Timing

Controls start visible. Each start, explicit reveal, or return to a running
session replaces the hide deadline with `now + 4000 ms`. Controls do not
auto-hide while the session is paused or a capture request has failed.

