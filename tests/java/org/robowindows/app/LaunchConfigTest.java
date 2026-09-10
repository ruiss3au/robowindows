package org.robowindows.app;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;

public final class LaunchConfigTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        for (boolean debug : new boolean[]{false, true}) {
            for (boolean experimental : new boolean[]{false, true}) {
                require(DynamicCyclePolicy.FIXED_20K.available(debug, experimental), "20k opt-in available");
                require(DynamicCyclePolicy.FIXED_30K.available(debug, experimental) == (debug && experimental), "30k diagnostic only");
                require(DynamicCyclePolicy.AUTO_80_LIMIT_30K.available(debug, experimental) == (debug && experimental), "auto diagnostic only");
            }
        }
        String floppy = LaunchConfig.create("/private/disk.img", "img", 16, "normal", false);
        require(floppy.contains("memsize=16"), "memory missing");
        require(floppy.contains("core=normal"), "CPU missing");
        require(floppy.contains("nosound=true"), "sound setting missing");
        require(floppy.contains("boot \"/private/disk.img\""), "disk must direct boot");
        require(!floppy.contains("PUREMENU"), "upstream menu command prohibited");

        require(LaunchConfig.supportsMedia("iso"), "ISO must be attachable media");
        require(!LaunchConfig.supportsBoot("iso"), "ISO must not claim direct boot support");

        String installer = LaunchConfig.createWindowsInstall("/private/disk.img",
                "/private/source.iso", "/private/cdboot.img", true, 64, "normal", true);
        require(installer.contains("imgmount 2 \"/private/disk.img\" -t hdd -fs none -size " +
                WindowsInstallMedia.DISK_GEOMETRY), "Windows disk geometry missing");
        require(installer.contains("imgmount d \"/private/source.iso\" -t iso"),
                "Windows CD missing");
        require(installer.contains("cputype=pentium_slow"), "Windows CPU type missing");
        require(installer.contains("[sblaster]\nsbtype=sb16\nsbbase=220\nirq=7\ndma=1\nhdma=5"),
                "Windows Sound Blaster resources missing");
        require(installer.contains("boot \"/private/cdboot.img\""),
                "installer floppy missing");
        String installed = LaunchConfig.createWindowsInstall("/private/disk.img",
                "/private/source.iso", "/private/cdboot.img", false, 64, "normal", true);
        require(installed.contains("boot -l c"), "hard disk boot missing");
        require(!installer.contains("PUREMENU") && !installed.contains("PUREMENU"),
                "Windows path must not expose upstream UI");
        require(installer.contains(":robowindows_boot\n") &&
                installer.contains("goto robowindows_boot"),
                "guest reboot must remain inside the RoboWindows boot loop");
        String dynamic20 = LaunchConfig.createWindowsInstall("/private/disk.img",
                "/private/source.iso", "/private/cdboot.img", false, 64, "dynamic",
                LaunchConfig.DYNAMIC_EXPERIMENTAL_CYCLES, true);
        String dynamic30 = LaunchConfig.createWindowsInstall("/private/disk.img",
                "/private/source.iso", "/private/cdboot.img", false, 64, "dynamic",
                LaunchConfig.DYNAMIC_PERFORMANCE_CYCLES, true);
        require(dynamic20.contains("core=dynamic\ncycles=fixed 20000"),
                "20k dynamic candidate missing");
        require(dynamic30.contains("core=dynamic\ncycles=fixed 30000"),
                "30k dynamic candidate missing");
        String dynamicAuto = LaunchConfig.createWindowsDynamic("/private/disk.img",
                "/private/source.iso", "/private/cdboot.img", false, 64,
                DynamicCyclePolicy.AUTO_80_LIMIT_30K, true);
        require(dynamicAuto.contains("core=dynamic\ncycles=auto 80% limit 30000\n"),
                "bounded automatic dynamic candidate missing");
        require(!dynamicAuto.contains("cycles=fixed"),
                "automatic candidate must have exactly one policy");
        require(LaunchConfig.isDynamicDiagnosticCycles(20000) &&
                LaunchConfig.isDynamicDiagnosticCycles(30000) &&
                !LaunchConfig.isDynamicDiagnosticCycles(12000) &&
                !LaunchConfig.isDynamicDiagnosticCycles(25000),
                "dynamic candidate allowlist");
        String utility = LaunchConfig.createWindowsUtility("/private/disk.img",
                "/private/source.iso", "/private/patch9x.img", 64, "auto", true);
        require(utility.contains("boot \"/private/patch9x.img\""),
                "utility floppy boot missing");
        require(utility.contains("imgmount 2 \"/private/disk.img\""),
                "utility boot must retain Windows disk");

        File root = Files.createTempDirectory("robowindows-eltorito").toFile();
        File iso = new File(root, "source.iso");
        File boot = new File(root, "boot.img");
        try {
            createSyntheticElToritoIso(iso);
            WindowsInstallMedia.extractElToritoFloppy(iso, boot);
            require(boot.length() == 1_474_560, "1.44 MB boot image size");
            byte[] extracted = Files.readAllBytes(boot.toPath());
            require((extracted[0] & 0xff) == 0x42 && (extracted[511] & 0xff) == 0x99,
                    "boot image content");
        } finally {
            boot.delete(); iso.delete(); root.delete();
        }
        if (args.length == 1) {
            File privateBoot = File.createTempFile("robowindows-private-boot", ".img");
            try {
                WindowsInstallMedia.extractElToritoFloppy(new File(args[0]), privateBoot);
                require(privateBoot.length() == 1_474_560,
                        "private ISO must expose a 1.44 MB boot image");
            } finally {
                privateBoot.delete();
            }
        }

        boolean rejected = false;
        try { LaunchConfig.create("/private/game.zip", "zip", 64, "auto", true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "unsupported media must be rejected");

        rejected = false;
        try { LaunchConfig.create("/private/disk.img", "img", 63, "auto", true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "invalid memory must be rejected");

        rejected = false;
        try {
            LaunchConfig.createWindowsInstall("/private/disk.img", "/private/source.iso",
                    "/private/cdboot.img", false, 64, "dynamic", 25000, true);
        } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "arbitrary dynamic cycles must be rejected");

        rejected = false;
        try { DynamicCyclePolicy.fromId("auto-unlimited"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "unlimited automatic policy must be rejected");
    }

    private static void createSyntheticElToritoIso(File iso) throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(iso, "rw")) {
            file.setLength(24L * 2048 + 1_474_560);
            byte[] record = new byte[2048];
            record[0] = 0;
            putAscii(record, 1, "CD001");
            putAscii(record, 7, "EL TORITO SPECIFICATION");
            putLittle32(record, 71, 20);
            file.seek(17L * 2048); file.write(record);
            byte[] catalog = new byte[2048];
            catalog[0] = 1; catalog[30] = 0x55; catalog[31] = (byte) 0xaa;
            int sum = 0;
            for (int i = 0; i < 32; i += 2) {
                if (i != 28) sum = (sum + (catalog[i] & 0xff) +
                        ((catalog[i + 1] & 0xff) << 8)) & 0xffff;
            }
            int checksum = (-sum) & 0xffff;
            catalog[28] = (byte) checksum; catalog[29] = (byte) (checksum >> 8);
            catalog[32] = (byte) 0x88; catalog[33] = 2;
            putLittle32(catalog, 40, 24);
            file.seek(20L * 2048); file.write(catalog);
            file.seek(24L * 2048); file.write(0x42);
            file.seek(24L * 2048 + 511); file.write(0x99);
        }
    }

    private static void putAscii(byte[] target, int offset, String text) {
        for (int i = 0; i < text.length(); i++) target[offset + i] = (byte) text.charAt(i);
    }

    private static void putLittle32(byte[] target, int offset, int value) {
        for (int i = 0; i < 4; i++) target[offset + i] = (byte) (value >> (i * 8));
    }
}
