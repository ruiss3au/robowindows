package org.robowindows.app;

import java.io.IOException;
import java.util.Arrays;

/** Parser for the bounded result sector written by the OS-free CPU fixture. */
final class CpuFixtureResult {
    static final int RECORD_BYTES = 22;
    static final int VERSION = 3;
    static final int EXPECTED_MASK = 0x3fff;
    private static final byte[] MAGIC = {'R', 'W', 'C', 'P', 'U', '0', '0', '3'};
    private static final long COMPLETE = 0xc0dec0deL;

    final byte[] record;
    final int mask;

    private CpuFixtureResult(byte[] record, int mask) {
        this.record = record;
        this.mask = mask;
    }

    static CpuFixtureResult parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length != RECORD_BYTES) {
            throw new IOException("CPU fixture result has the wrong size");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) throw new IOException("CPU fixture did not complete");
        }
        if (word(bytes, 8) != VERSION || word(bytes, 10) != EXPECTED_MASK) {
            throw new IOException("CPU fixture version is unsupported");
        }
        int mask = word(bytes, 12);
        if (dword(bytes, 16) != COMPLETE) throw new IOException("CPU fixture did not complete");
        int sum = 0;
        for (int i = 0; i < 20; i++) sum = (sum + (bytes[i] & 0xff)) & 0xffff;
        if (word(bytes, 20) != sum) throw new IOException("CPU fixture checksum failed");
        int assertion = word(bytes, 14);
        if (mask != EXPECTED_MASK || assertion != 0) {
            throw new IOException(String.format(
                    "CPU fixture failed test mask 0x%04x (assertion %d)", mask, assertion));
        }
        return new CpuFixtureResult(bytes.clone(), mask);
    }

    boolean matches(CpuFixtureResult other) {
        return other != null && Arrays.equals(record, other.record);
    }

    private static int word(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long dword(byte[] bytes, int offset) {
        return (word(bytes, offset) & 0xffffL) | ((long) word(bytes, offset + 2) << 16);
    }
}
