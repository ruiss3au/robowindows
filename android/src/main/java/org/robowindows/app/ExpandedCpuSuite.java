package org.robowindows.app;

import java.util.HashMap;
import java.util.Map;

/** Stable protocol-v4 suite catalogue. IDs and case counts are capability ABI. */
enum ExpandedCpuSuite {
    STRINGS(0x0101, "strings", 30),
    FAULT_RETRY(0x0102, "fault/retry", 8),
    PAGEFAULT_PROGRESS(0x0103, "page-fault progress", 3),
    INTEGER_FLAGS(0x0201, "integer/flags", 10),
    STACK_CONTROL(0x0202, "stack/control flow", 8),
    PAGING_SMC(0x0203, "paging/SMC", 8),
    X87(0x0301, "x87", 8),
    MIXED_SEEDS(0x0302, "mixed fixed-seed", 32);

    private static final Map<Integer, ExpandedCpuSuite> BY_ID = new HashMap<>();
    static {
        for (ExpandedCpuSuite suite : values()) BY_ID.put(suite.id, suite);
    }

    final int id;
    final String label;
    final int caseCount;
    final long expectedMask;
    final long expectedSignature;

    ExpandedCpuSuite(int id, String label, int caseCount) {
        this.id = id;
        this.label = label;
        this.caseCount = caseCount;
        this.expectedMask = caseCount == 64 ? -1L : (1L << caseCount) - 1;
        this.expectedSignature = 0x5257000000000000L | ((long) id << 32) | caseCount;
    }

    static ExpandedCpuSuite fromId(int id) {
        return BY_ID.get(id);
    }

    static String capabilitySchema() {
        StringBuilder value = new StringBuilder("expanded-x86-v4");
        for (ExpandedCpuSuite suite : values()) {
            value.append('|').append(Integer.toHexString(suite.id))
                    .append(':').append(suite.caseCount);
        }
        return value.toString();
    }
}
