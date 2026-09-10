package org.robowindows.app;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Internal fixture identities and bounded result validation; no media paths. */
final class PresentationWorkload {
    static boolean allowed(String workload) {
        return "tone".equals(workload) || "stress".equals(workload);
    }

    static long ticks(String workload, byte[] record) {
        if (!allowed(workload) || record == null || record.length < 14) return -1;
        boolean stress = "stress".equals(workload);
        String magic = new String(record, 0, 8, StandardCharsets.US_ASCII);
        if (!(stress ? "RWSTR001" : "RWGPU001").equals(magic)) return -1;
        ByteBuffer bytes = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.getShort(12) != 1) return -1;
        if (stress) {
            if (record.length < 32 || bytes.getShort(14) != 15) return -1;
            for (int i = 0; i < 4; ++i) if (bytes.getInt(16 + i * 4) == 0) return -1;
        }
        long ticks = bytes.getInt(8) & 0xffffffffL;
        return ticks > 0 ? ticks : -1;
    }
}
