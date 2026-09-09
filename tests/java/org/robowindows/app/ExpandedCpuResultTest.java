package org.robowindows.app;

import java.io.IOException;

public final class ExpandedCpuResultTest {
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    private static byte[] complete(ExpandedCpuSuite suite) {
        byte[] record = new byte[ExpandedCpuResult.RECORD_BYTES];
        byte[] magic = {'R', 'W', 'X', '8', '6', 'G', '0', '4'};
        System.arraycopy(magic, 0, record, 0, magic.length);
        putWord(record, 8, ExpandedCpuResult.VERSION);
        putWord(record, 10, record.length);
        putWord(record, 12, suite.id);
        putWord(record, 14, suite.caseCount);
        putQword(record, 16, suite.expectedMask);
        putQword(record, 24, suite.expectedMask);
        putQword(record, 32, suite.expectedSignature);
        putDword(record, 44, ExpandedCpuResult.COMPLETE);
        refreshIntegrity(record);
        return record;
    }

    private static void refreshIntegrity(byte[] record) {
        putDword(record, 48, ExpandedCpuResult.fnv1a(record, 48));
    }

    private static void putWord(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void putDword(byte[] bytes, int offset, long value) {
        putWord(bytes, offset, value);
        putWord(bytes, offset + 2, value >>> 16);
    }

    private static void putQword(byte[] bytes, int offset, long value) {
        putDword(bytes, offset, value);
        putDword(bytes, offset + 4, value >>> 32);
    }

    private static void rejects(byte[] record, String name) {
        try {
            ExpandedCpuResult.parse(record);
            throw new AssertionError(name);
        } catch (IOException expected) {
            // Expected.
        }
    }

    public static void main(String[] args) throws Exception {
        for (ExpandedCpuSuite suite : ExpandedCpuSuite.values()) {
            ExpandedCpuResult parsed = ExpandedCpuResult.parse(complete(suite));
            require(parsed.suite == suite, "suite round trip " + suite);
        }

        rejects(null, "null rejected");
        rejects(new byte[4097], "oversized rejected");
        rejects(new byte[52], "missing magic rejected");

        byte[] wrongLength = complete(ExpandedCpuSuite.STRINGS);
        putWord(wrongLength, 10, 43);
        refreshIntegrity(wrongLength);
        rejects(wrongLength, "declared length rejected");

        byte[] wrongVersion = complete(ExpandedCpuSuite.STRINGS);
        putWord(wrongVersion, 8, 3);
        refreshIntegrity(wrongVersion);
        rejects(wrongVersion, "stale version rejected");

        byte[] unknown = complete(ExpandedCpuSuite.STRINGS);
        putWord(unknown, 12, 0x7fff);
        refreshIntegrity(unknown);
        rejects(unknown, "unknown suite rejected");

        byte[] wrongCases = complete(ExpandedCpuSuite.STRINGS);
        putWord(wrongCases, 14, 29);
        refreshIntegrity(wrongCases);
        rejects(wrongCases, "case count rejected");

        byte[] incomplete = complete(ExpandedCpuSuite.FAULT_RETRY);
        incomplete[24] &= ~1;
        refreshIntegrity(incomplete);
        rejects(incomplete, "partial suite rejected");

        byte[] assertion = complete(ExpandedCpuSuite.INTEGER_FLAGS);
        putWord(assertion, 40, 207);
        refreshIntegrity(assertion);
        rejects(assertion, "assertion rejected");

        byte[] reserved = complete(ExpandedCpuSuite.X87);
        putWord(reserved, 42, 1);
        refreshIntegrity(reserved);
        rejects(reserved, "reserved data rejected");

        byte[] corrupt = complete(ExpandedCpuSuite.PAGING_SMC);
        corrupt[32] ^= 1;
        rejects(corrupt, "integrity rejected");

        ExpandedCpuReport report = new ExpandedCpuReport();
        for (ExpandedCpuSuite suite : ExpandedCpuSuite.values()) {
            report.add(ExpandedCpuReport.Mode.NORMAL, complete(suite));
        }
        require(!report.isComplete(), "normal-only report incomplete");
        try {
            report.requireComplete();
            throw new AssertionError("missing dynamic suites accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("missing"), "missing suite identified");
        }
        for (ExpandedCpuSuite suite : ExpandedCpuSuite.values()) {
            report.add(ExpandedCpuReport.Mode.DYNAMIC, complete(suite));
        }
        report.requireComplete();
        require(report.isComplete(), "complete report accepted");

        try {
            report.add(ExpandedCpuReport.Mode.NORMAL, complete(ExpandedCpuSuite.STRINGS));
            throw new AssertionError("duplicate suite accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("duplicated"), "duplicate identified");
        }

        System.out.println("Expanded CPU result checks passed");
    }
}
