package org.robowindows.app;

/** Binder messages for the machine-independent CPU correctness fixture. */
final class CpuFixtureProtocol {
    static final int START = 1;
    static final int RESULT = 2;
    static final String ERROR = "error";
    static final String RECORD = "record";
    static final String MODE = "mode";
    static final String SUITE_ID = "suite_id";
    static final int LEGACY_SUITE_ID = -1;

    private CpuFixtureProtocol() {}
}
