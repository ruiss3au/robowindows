package org.robowindows.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Display-only preference: never controls native health polling or logging. */
final class PerformanceDisplayPreferences {
    private final SharedPreferences preferences;
    PerformanceDisplayPreferences(Context context) {
        preferences = context.getSharedPreferences("performance_display", Context.MODE_PRIVATE);
    }
    boolean visible() { return preferences.getBoolean("show_counters", false); }
    boolean setVisible(boolean visible) {
        return preferences.edit().putBoolean("show_counters", visible).commit();
    }
}
