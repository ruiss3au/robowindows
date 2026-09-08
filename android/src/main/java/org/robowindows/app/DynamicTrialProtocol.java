package org.robowindows.app;

/** Binder message names shared by the host UI and the isolated dynrec service. */
final class DynamicTrialProtocol {
    static final int START = 1;
    static final int STOP = 2;
    static final int PAUSE = 3;
    static final int SURFACE = 4;
    static final int KEY = 5;
    static final int MOUSE = 6;
    static final int TOUCH = 7;
    static final int CANCEL_INPUT = 8;
    static final int CHANGE_MEDIA = 9;
    static final int STATUS = 10;

    static final String LAUNCH_PATH = "launchPath";
    static final String FILES_PATH = "filesPath";
    static final String MACHINE_ID = "machineId";
    static final String ATTEMPT_ID = "attemptId";
    static final String GENERATION = "generation";
    static final String SURFACE_VALUE = "surface";
    static final String PAUSED = "paused";
    static final String STATUS_VALUE = "status";
    static final String ERROR = "error";
    static final String ACTION = "action";
    static final String KEY_CODE = "keyCode";
    static final String SCAN_CODE = "scanCode";
    static final String REPEAT_COUNT = "repeatCount";
    static final String META_STATE = "metaState";
    static final String SOURCE = "source";
    static final String DEVICE = "device";
    static final String EVENT_NANOS = "eventNanos";
    static final String RELATIVE_X = "relativeX";
    static final String RELATIVE_Y = "relativeY";
    static final String ABSOLUTE_X = "absoluteX";
    static final String ABSOLUTE_Y = "absoluteY";
    static final String BUTTON_STATE = "buttonState";
    static final String ACTION_BUTTON = "actionButton";
    static final String V_SCROLL = "vScroll";
    static final String H_SCROLL = "hScroll";
    static final String CAPTURED = "captured";
    static final String POINTER_COUNT = "pointerCount";
    static final String PRESSURE = "pressure";

    private DynamicTrialProtocol() {}
}
