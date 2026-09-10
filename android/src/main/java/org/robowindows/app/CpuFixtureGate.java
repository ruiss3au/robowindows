package org.robowindows.app;

import android.content.Context;

/** Build-specific capability gate; it contains no machine or device data. */
final class CpuFixtureGate {
    private static final String PREFS = "cpu_fixture_gate";
    private static final String PASSED_TOKEN = "passed_token";
    private static final String RESULT_TOKEN = "result_token";
    private static final String RESULT = "result";

    private CpuFixtureGate() {}

    static boolean passed(Context context) {
        return token().equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PASSED_TOKEN, ""));
    }

    static boolean markPassed(Context context, ExpandedCpuReport report) {
        if (report == null || !report.isComplete()) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PASSED_TOKEN, token()).putString(RESULT_TOKEN, token())
                .putString(RESULT, "Passed").commit();
    }

    static boolean begin(Context context) { return recordUnqualified(context, "Incomplete"); }

    static boolean recordUnqualified(Context context, String result) {
        if (!result.equals("Incomplete") && !result.equals("Failed") && !result.equals("Cancelled")) {
            throw new IllegalArgumentException("Unknown CPU test outcome");
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(PASSED_TOKEN).putString(RESULT_TOKEN, token()).putString(RESULT, result).commit();
    }

    static boolean attempted(Context context) {
        return passed(context) || token().equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(RESULT_TOKEN, ""));
    }

    static String summary(Context context) {
        if (passed(context)) return "CPU correctness: passed for this core build.";
        String result = attempted(context) ? context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(RESULT, "Incomplete") : "Not tested";
        return "CPU correctness: " + result + " for this core build. A complete pass enables DynRec.";
    }

    private static String token() {
        return BuildConfig.CPU_DIAGNOSTIC_REVISION + "|robowindows-cpu-v3|" +
                CpuFixtureResult.EXPECTED_MASK + "|" + ExpandedCpuSuite.capabilitySchema();
    }
}
