package org.robowindows.app;

import java.io.IOException;

public final class CpuFixtureResultTest {
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    private static byte[] completeRecord() {
        byte[] record = new byte[CpuFixtureResult.RECORD_BYTES];
        byte[] magic = {'R', 'W', 'C', 'P', 'U', '0', '0', '3'};
        System.arraycopy(magic, 0, record, 0, magic.length);
        putWord(record, 8, CpuFixtureResult.VERSION);
        putWord(record, 10, CpuFixtureResult.EXPECTED_MASK);
        putWord(record, 12, CpuFixtureResult.EXPECTED_MASK);
        record[16] = (byte) 0xde;
        record[17] = (byte) 0xc0;
        record[18] = (byte) 0xde;
        record[19] = (byte) 0xc0;
        int sum = 0;
        for (int i = 0; i < 20; i++) sum = (sum + (record[i] & 0xff)) & 0xffff;
        putWord(record, 20, sum);
        return record;
    }

    private static void putWord(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void rejects(byte[] record, String name) {
        try {
            CpuFixtureResult.parse(record);
            throw new AssertionError(name);
        } catch (IOException expected) {
            // Expected rejection.
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] complete = completeRecord();
        CpuFixtureResult normal = CpuFixtureResult.parse(complete);
        CpuFixtureResult dynamic = CpuFixtureResult.parse(complete.clone());
        require(normal.mask == CpuFixtureResult.EXPECTED_MASK, "complete mask accepted");
        require(normal.matches(dynamic), "matching modes accepted");

        byte[] incomplete = completeRecord();
        putWord(incomplete, 12, 0x1fff);
        int sum = 0;
        for (int i = 0; i < 20; i++) sum = (sum + (incomplete[i] & 0xff)) & 0xffff;
        putWord(incomplete, 20, sum);
        rejects(incomplete, "incomplete mask rejected");

        byte[] assertion = completeRecord();
        putWord(assertion, 14, 3);
        sum = 0;
        for (int i = 0; i < 20; i++) sum = (sum + (assertion[i] & 0xff)) & 0xffff;
        putWord(assertion, 20, sum);
        rejects(assertion, "nonzero assertion rejected with complete mask");
        try {
            CpuFixtureResult.parse(assertion);
            throw new AssertionError("assertion record accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("assertion 3"),
                    "fixed assertion identifier reported");
        }

        byte[] corrupt = completeRecord();
        corrupt[20] ^= 1;
        rejects(corrupt, "checksum rejected");
        rejects(new byte[CpuFixtureResult.RECORD_BYTES], "unfinished record rejected");
        rejects(new byte[3], "wrong size rejected");
        System.out.println("CPU fixture result checks passed");
    }
}
