package org.robowindows.app;

import android.content.Context;

/** Build-specific capability gate; it contains no machine or device data. */
final class CpuFixtureGate {
    private static final String PREFS = "cpu_fixture_gate";
    private static final String PASSED_TOKEN = "passed_token";

    private CpuFixtureGate() {}

    static boolean passed(Context context) {
        return token().equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PASSED_TOKEN, ""));
    }

    static boolean markPassed(Context context, ExpandedCpuReport report) {
        if (report == null || !report.isComplete()) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PASSED_TOKEN, token()).commit();
    }

    private static String token() {
        return BuildConfig.CPU_DIAGNOSTIC_REVISION + "|robowindows-cpu-v3|" +
                CpuFixtureResult.EXPECTED_MASK + "|" + ExpandedCpuSuite.capabilitySchema();
    }
}
