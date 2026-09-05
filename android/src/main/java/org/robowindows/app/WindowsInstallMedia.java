package org.robowindows.app;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

final class WindowsInstallMedia {
    static final long DISK_BYTES = 512L * 63 * 16 * 4063;
    static final String DISK_GEOMETRY = "512,63,16,4063";
    private static final int ISO_SECTOR = 2048;

    private WindowsInstallMedia() {}

    static void extractElToritoFloppy(File iso, File output) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(iso, "r")) {
            byte[] record = readAt(input, 17L * ISO_SECTOR, ISO_SECTOR);
            if ((record[0] & 0xff) != 0 || !asciiEquals(record, 1, "CD001") ||
                    !asciiEquals(record, 7, "EL TORITO SPECIFICATION")) {
                throw new IOException("The ISO has no supported El Torito boot record");
            }
            long catalogLba = little32(record, 71);
            byte[] catalog = readAt(input, catalogLba * ISO_SECTOR, ISO_SECTOR);
            if ((catalog[0] & 0xff) != 1 || (catalog[30] & 0xff) != 0x55 ||
                    (catalog[31] & 0xff) != 0xaa || checksum16(catalog, 0, 32) != 0) {
                throw new IOException("The ISO boot catalog is invalid");
            }
            if ((catalog[32] & 0xff) != 0x88) {
                throw new IOException("The ISO has no bootable default entry");
            }
            int mediaType = catalog[33] & 0xff;
            int imageBytes;
            if (mediaType == 1) imageBytes = 1_228_800;
            else if (mediaType == 2) imageBytes = 1_474_560;
            else if (mediaType == 3) imageBytes = 2_949_120;
            else throw new IOException("Windows installation requires a bootable floppy-emulation ISO");
            long imageLba = little32(catalog, 40);
            byte[] buffer = new byte[128 * 1024];
            long remaining = imageBytes;
            input.seek(imageLba * ISO_SECTOR);
            try (FileOutputStream out = new FileOutputStream(output)) {
                while (remaining > 0) {
                    int wanted = (int) Math.min(buffer.length, remaining);
                    int read = input.read(buffer, 0, wanted);
                    if (read < 0) throw new EOFException("Truncated ISO boot image");
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
                out.getFD().sync();
            }
        } catch (IOException error) {
            output.delete();
            throw error;
        }
    }

    static void createBlankDisk(File output) throws IOException {
        try (RandomAccessFile disk = new RandomAccessFile(output, "rw")) {
            disk.setLength(DISK_BYTES);
            disk.getFD().sync();
        } catch (IOException error) {
            output.delete();
            throw error;
        }
    }

    private static byte[] readAt(RandomAccessFile file, long offset, int size) throws IOException {
        byte[] data = new byte[size];
        file.seek(offset);
        file.readFully(data);
        return data;
    }

    private static boolean asciiEquals(byte[] data, int offset, String expected) {
        for (int i = 0; i < expected.length(); i++) {
            if ((data[offset + i] & 0xff) != expected.charAt(i)) return false;
        }
        return true;
    }

    private static long little32(byte[] data, int offset) {
        return (data[offset] & 0xffL) | ((data[offset + 1] & 0xffL) << 8) |
                ((data[offset + 2] & 0xffL) << 16) | ((data[offset + 3] & 0xffL) << 24);
    }

    private static int checksum16(byte[] data, int offset, int size) {
        int sum = 0;
        for (int i = offset; i < offset + size; i += 2) {
            sum = (sum + (data[i] & 0xff) + ((data[i + 1] & 0xff) << 8)) & 0xffff;
        }
        return sum;
    }
}
