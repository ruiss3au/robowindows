package org.robowindows.app;

/** Named, allowlisted cycle policies for guarded DynRec diagnostics. */
enum DynamicCyclePolicy {
    FIXED_20K("fixed-20k", "fixed 20000", "DYNREC 20K · EXPERIMENTAL"),
    FIXED_30K("fixed-30k", "fixed 30000", "DYNREC 30K · EXPERIMENTAL"),
    AUTO_80_LIMIT_30K("auto-80-limit-30k", "auto 80% limit 30000",
            "DYNREC AUTO 80% · MAX 30K · EXPERIMENTAL");

    final String id;
    final String configValue;
    final String label;

    DynamicCyclePolicy(String id, String configValue, String label) {
        this.id = id;
        this.configValue = configValue;
        this.label = label;
    }

    static DynamicCyclePolicy fromId(String id) {
        for (DynamicCyclePolicy policy : values()) {
            if (policy.id.equals(id)) return policy;
        }
        throw new IllegalArgumentException("Unsupported dynamic diagnostic policy");
    }

    static DynamicCyclePolicy fromFixedCycles(int cycles) {
        if (cycles == LaunchConfig.DYNAMIC_EXPERIMENTAL_CYCLES) return FIXED_20K;
        if (cycles == LaunchConfig.DYNAMIC_PERFORMANCE_CYCLES) return FIXED_30K;
        throw new IllegalArgumentException("Unsupported dynamic diagnostic cycle value");
    }
}
