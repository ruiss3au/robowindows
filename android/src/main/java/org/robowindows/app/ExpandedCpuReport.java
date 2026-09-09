package org.robowindows.app;

import java.io.IOException;
import java.util.EnumMap;

/** Fail-closed normal/dynamic aggregate for the complete expanded gate. */
final class ExpandedCpuReport {
    enum Mode { NORMAL, DYNAMIC }

    private final EnumMap<ExpandedCpuSuite, ExpandedCpuResult> normal =
            new EnumMap<>(ExpandedCpuSuite.class);
    private final EnumMap<ExpandedCpuSuite, ExpandedCpuResult> dynamic =
            new EnumMap<>(ExpandedCpuSuite.class);

    void add(Mode mode, byte[] record) throws IOException {
        if (mode == null) throw new IOException("Expanded CPU mode is missing");
        ExpandedCpuResult result = ExpandedCpuResult.parse(record);
        EnumMap<ExpandedCpuSuite, ExpandedCpuResult> target =
                mode == Mode.NORMAL ? normal : dynamic;
        if (target.putIfAbsent(result.suite, result) != null) {
            throw new IOException(String.format(
                    "Expanded CPU suite 0x%04x is duplicated for %s",
                    result.suite.id, mode.name().toLowerCase()));
        }
    }

    boolean isComplete() {
        for (ExpandedCpuSuite suite : ExpandedCpuSuite.values()) {
            ExpandedCpuResult baseline = normal.get(suite);
            if (baseline == null || !baseline.matches(dynamic.get(suite))) return false;
        }
        return normal.size() == ExpandedCpuSuite.values().length &&
                dynamic.size() == ExpandedCpuSuite.values().length;
    }

    void requireComplete() throws IOException {
        for (ExpandedCpuSuite suite : ExpandedCpuSuite.values()) {
            ExpandedCpuResult baseline = normal.get(suite);
            ExpandedCpuResult candidate = dynamic.get(suite);
            if (baseline == null || candidate == null) {
                throw new IOException("Expanded CPU suite is missing: " + suite.label);
            }
            if (!baseline.matches(candidate)) {
                throw new IOException("Normal and dynamic results differ: " + suite.label);
            }
        }
        if (!isComplete()) throw new IOException("Expanded CPU report is incomplete");
    }
}
