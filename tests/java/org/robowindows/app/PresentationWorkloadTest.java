package org.robowindows.app;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class PresentationWorkloadTest {
    public static void main(String[] args) {
        if (!PresentationWorkload.allowed("tone") || !PresentationWorkload.allowed("stress") ||
                PresentationWorkload.allowed(null) || PresentationWorkload.allowed("../stress")) throw new AssertionError();
        byte[] record = new byte[32];
        ByteBuffer data = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
        data.put("RWSTR001".getBytes(StandardCharsets.US_ASCII));
        data.putInt(630).putShort((short) 1).putShort((short) 15);
        for (int i = 0; i < 4; ++i) data.putInt(1);
        check(630, PresentationWorkload.ticks("stress", record));
        check(-1, PresentationWorkload.ticks("tone", record));
        check(-1, PresentationWorkload.ticks("stress", new byte[14]));
        check(-1, PresentationWorkload.ticks("invalid", record));
        check(-1, PresentationWorkload.ticks("stress", null));
        for (int i = 0; i < 4; ++i) {
            data.putShort(14, (short) (15 ^ (1 << i)));
            check(-1, PresentationWorkload.ticks("stress", record));
            data.putShort(14, (short) 15);
            data.putInt(16 + i * 4, 0);
            check(-1, PresentationWorkload.ticks("stress", record));
            data.putInt(16 + i * 4, 1);
        }
        data.putShort(12, (short) 257);
        check(-1, PresentationWorkload.ticks("stress", record));
        data.putShort(12, (short) 1);
        data.putInt(8, 0);
        check(-1, PresentationWorkload.ticks("stress", record));
        data.putInt(8, 630);
        System.arraycopy("RWGPU001".getBytes(StandardCharsets.US_ASCII), 0, record, 0, 8);
        check(630, PresentationWorkload.ticks("tone", record));
        check(-1, PresentationWorkload.ticks("stress", record));
        System.out.println("Presentation workload identity and phase checks passed");
    }
    private static void check(long expected, long actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
}
