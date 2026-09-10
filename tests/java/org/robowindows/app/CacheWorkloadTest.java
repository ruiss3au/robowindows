package org.robowindows.app;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class CacheWorkloadTest {
    private static void require(boolean value) { if (!value) throw new AssertionError(); }
    private static void seal(byte[] r) {
        ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN).putInt(60, CacheWorkload.integrity(r));
    }
    public static void main(String[] args) throws Exception {
        require(!CacheWorkload.allowed(null));
        require(!CacheWorkload.allowed("../warm"));
        require(!CacheWorkload.allowed("cache-WARM"));
        require(CacheWorkload.parse("cache-warm", null) == null);
        require(CacheWorkload.parse("cache-warm", new byte[63]) == null);
        require(CacheWorkload.parse("cache-warm", new byte[65]) == null);
        require(CacheWorkload.checksum(1) == 3540426752L);
        require(CacheWorkload.checksum(5) == 3540428800L);
        for (int id = 1; id <= 5; ++id) {
            String phase = "cache-" + CacheWorkload.PHASES[id - 1];
            byte[] record = Files.readAllBytes(Paths.get(args[id - 1]));
            CacheWorkload.Result result = CacheWorkload.parse(phase, record);
            require(result != null);
            System.out.println("PASS " + phase + result.fields());
            for (int offset = 0; offset < 64; ++offset) {
                byte[] bad = record.clone(); bad[offset] ^= 1;
                require(CacheWorkload.parse(phase, bad) == null);
            }
            for (int offset : new int[]{0, 4, 8, 12, 16, 20, 24, 28, 44, 48, 52, 56}) {
                byte[] bad = record.clone(); bad[offset] ^= 1; seal(bad);
                require(CacheWorkload.parse(phase, bad) == null);
            }
            for (int offset : new int[]{32, 36, 40}) {
                byte[] bad = record.clone();
                ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, -1); seal(bad);
                require(CacheWorkload.parse(phase, bad) == null);
            }
            byte[] bad = record.clone();
            ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 30001); seal(bad);
            require(CacheWorkload.parse(phase, bad) == null);
            bad = record.clone();
            ByteBuffer b = ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(40, b.getInt(36)); seal(bad);
            require(CacheWorkload.parse(phase, bad) == null);
            require(CacheWorkload.parse("cache-" + CacheWorkload.PHASES[id % 5], record) == null);
        }
        byte[] failure = Files.readAllBytes(Paths.get(args[5]));
        require(CacheWorkload.parse("cache-warm", failure) == null);
        require(ByteBuffer.wrap(failure).order(ByteOrder.LITTLE_ENDIAN).getInt(48) == 1);
        require(CacheWorkload.integrity(failure) == ByteBuffer.wrap(failure).order(ByteOrder.LITTLE_ENDIAN).getInt(60));
        System.out.println("Cache fixture checksums, negative records and injected guest failure passed");
    }
}
