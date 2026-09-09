package org.robowindows.app;

import java.io.IOException;
import java.util.Arrays;

/** Strict parser for one bounded expanded x86 suite record. */
final class ExpandedCpuResult {
    static final int VERSION = 4;
    static final int RECORD_BYTES = 52;
    static final int MAX_RECORD_BYTES = 4096;
    static final long COMPLETE = 0xc04ec04eL;
    private static final byte[] MAGIC = {'R', 'W', 'X', '8', '6', 'G', '0', '4'};

    final ExpandedCpuSuite suite;
    final byte[] record;

    private ExpandedCpuResult(ExpandedCpuSuite suite, byte[] record) {
        this.suite = suite;
        this.record = record;
    }

    static ExpandedCpuResult parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_RECORD_BYTES) {
            throw new IOException("Expanded CPU record length is invalid");
        }
        if (bytes.length != RECORD_BYTES || word(bytes, 10) != RECORD_BYTES) {
            throw new IOException("Expanded CPU record has the wrong size");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) throw new IOException("Expanded CPU record magic is invalid");
        }
        if (word(bytes, 8) != VERSION) {
            throw new IOException("Expanded CPU record version is unsupported");
        }
        ExpandedCpuSuite suite = ExpandedCpuSuite.fromId(word(bytes, 12));
        if (suite == null) throw new IOException("Expanded CPU suite is unknown");
        if (word(bytes, 14) != suite.caseCount) {
            throw new IOException("Expanded CPU suite case count is invalid");
        }
        if (word(bytes, 42) != 0 || dword(bytes, 44) != COMPLETE) {
            throw new IOException("Expanded CPU record did not complete");
        }
        if (dword(bytes, 48) != fnv1a(bytes, 48)) {
            throw new IOException("Expanded CPU record integrity check failed");
        }
        long expected = qword(bytes, 16);
        long completed = qword(bytes, 24);
        if (qword(bytes, 32) != suite.expectedSignature) {
            throw new IOException("Expanded CPU suite outcome is invalid");
        }
        int assertion = word(bytes, 40);
        if (expected != suite.expectedMask || completed != expected || assertion != 0) {
            throw new IOException(String.format(
                    "Expanded CPU suite 0x%04x failed mask 0x%016x (assertion %d)",
                    suite.id, completed, assertion));
        }
        return new ExpandedCpuResult(suite, bytes.clone());
    }

    boolean matches(ExpandedCpuResult other) {
        return other != null && suite == other.suite && Arrays.equals(record, other.record);
    }

    private static int word(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long dword(byte[] bytes, int offset) {
        return (word(bytes, offset) & 0xffffL) | ((long) word(bytes, offset + 2) << 16);
    }

    private static long qword(byte[] bytes, int offset) {
        return dword(bytes, offset) | (dword(bytes, offset + 4) << 32);
    }

    static long fnv1a(byte[] bytes, int length) {
        long hash = 0x811c9dc5L;
        for (int i = 0; i < length; i++) {
            hash ^= bytes[i] & 0xffL;
            hash = (hash * 0x01000193L) & 0xffffffffL;
        }
        return hash;
    }
}
