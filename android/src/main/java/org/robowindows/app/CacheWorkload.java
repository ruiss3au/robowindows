package org.robowindows.app;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Source-owned fixture protocol only; never accepts media paths or guest contents. */
final class CacheWorkload {
    static final String[] PHASES = {"warm", "cold", "reuse", "data", "rewrite"};
    static int id(String workload) {
        for (int i = 0; i < PHASES.length; ++i)
            if (("cache-" + PHASES[i]).equals(workload)) return i + 1;
        return 0;
    }
    static boolean allowed(String workload) { return id(workload) != 0; }
    static long checksum(int id) {
        int value = 0x12345678;
        for (int i = 0; i < 512; ++i)
            value = Integer.rotateLeft(value * 1664525 + 1013904223, 7);
        return (id == 5 ? value * 2048 + (value ^ 1) * 2048 : value * 4096) & 0xffffffffL;
    }
    static int integrity(byte[] record) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < 60; ++i) hash = (hash ^ (record[i] & 255)) * 0x01000193;
        return hash;
    }
    static Result parse(String workload, byte[] record) {
        int id = id(workload);
        if (id == 0 || record == null || record.length != 64) return null;
        ByteBuffer b = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
        if (!"RWCACHE1".equals(new String(record, 0, 8, StandardCharsets.US_ASCII)) ||
                b.getShort(8) != 1 || b.getShort(10) != 64 || b.getInt(12) != id ||
                b.getInt(16) != 4096 || b.getInt(20) != 4096 ||
                (b.getInt(24) & 0xffffffffL) != checksum(id) || b.getInt(28) != 262144 ||
                b.getInt(44) != 0xc04ec04e || b.getInt(48) != 0 ||
                b.getInt(52) != 512 || b.getInt(56) != 64 || b.getInt(60) != integrity(record)) return null;
        int setup = b.getInt(32), start = b.getInt(36), end = b.getInt(40);
        if (setup < 0 || start < setup || end <= start || end > 30000) return null;
        return new Result(setup, start - setup, end - start, end, checksum(id));
    }
    static final class Result {
        final int setupTicks, warmupTicks, measuredTicks, totalTicks;
        final long checksum;
        Result(int setup, int warmup, int measured, int total, long checksum) {
            setupTicks = setup; warmupTicks = warmup; measuredTicks = measured;
            totalTicks = total; this.checksum = checksum;
        }
        String fields() {
            return " calls=4096 checksum=" + checksum + " setup_ticks=" + setupTicks +
                    " warmup_ticks=" + warmupTicks + " measured_ticks=" + measuredTicks +
                    " total_ticks=" + totalTicks + " pit_divisor=1193";
        }
    }
}
