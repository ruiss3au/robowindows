package org.robowindows.app;

final class LaunchConfig {
    private LaunchConfig() {}

    static boolean supportsBoot(String extension) {
        return extension.equals("img") || extension.equals("ima") || extension.equals("vhd");
    }

    static boolean supportsMedia(String extension) {
        return supportsBoot(extension) || extension.equals("iso");
    }

    static String create(String mediaPath, String extension, int memoryMb, String cpuCore,
            boolean soundEnabled) {
        return create(mediaPath, extension, memoryMb, cpuCore, 0, soundEnabled);
    }

    static String create(String mediaPath, String extension, int memoryMb, String cpuCore,
            int fixedCycles, boolean soundEnabled) {
        if (!supportsBoot(extension)) throw new IllegalArgumentException("Unsupported boot media");
        if (memoryMb != 16 && memoryMb != 32 && memoryMb != 64 && memoryMb != 128) {
            throw new IllegalArgumentException("Unsupported memory size");
        }
        if (!cpuCore.equals("auto") && !cpuCore.equals("normal") &&
                !cpuCore.equals("dynamic")) {
            throw new IllegalArgumentException("Unsupported CPU core");
        }
        String escaped = mediaPath.replace("\\", "\\\\").replace("\"", "\\\"");
        String command = "boot \"" + escaped + "\"";
        validateCycles(cpuCore, fixedCycles);
        return "[dosbox]\nmemsize=" + memoryMb + "\n[cpu]\ncore=" + cpuCore +
                cycleLine(fixedCycles) +
                "\n[mixer]\nnosound=" + (!soundEnabled) +
                "\n[autoexec]\n@echo off\n" + command + "\n";
    }

    static String createWindowsInstall(String diskPath, String isoPath, String bootFloppyPath,
            boolean bootInstaller, int memoryMb, String cpuCore, boolean soundEnabled) {
        return createWindowsBoot(diskPath, isoPath, bootInstaller ? bootFloppyPath : null,
                memoryMb, cpuCore, 0, soundEnabled);
    }

    static String createWindowsInstall(String diskPath, String isoPath, String bootFloppyPath,
            boolean bootInstaller, int memoryMb, String cpuCore, int fixedCycles,
            boolean soundEnabled) {
        return createWindowsBoot(diskPath, isoPath, bootInstaller ? bootFloppyPath : null,
                memoryMb, cpuCore, fixedCycles, soundEnabled);
    }

    static String createWindowsUtility(String diskPath, String isoPath, String utilityDiskPath,
            int memoryMb, String cpuCore, boolean soundEnabled) {
        return createWindowsUtility(diskPath, isoPath, utilityDiskPath, memoryMb, cpuCore, 0,
                soundEnabled);
    }

    static String createWindowsUtility(String diskPath, String isoPath, String utilityDiskPath,
            int memoryMb, String cpuCore, int fixedCycles, boolean soundEnabled) {
        if (utilityDiskPath == null || utilityDiskPath.isEmpty()) {
            throw new IllegalArgumentException("Utility disk is required");
        }
        return createWindowsBoot(diskPath, isoPath, utilityDiskPath, memoryMb, cpuCore,
                fixedCycles, soundEnabled);
    }

    private static String createWindowsBoot(String diskPath, String isoPath, String floppyPath,
            int memoryMb, String cpuCore, int fixedCycles, boolean soundEnabled) {
        validate(memoryMb, cpuCore);
        validateCycles(cpuCore, fixedCycles);
        String disk = escape(diskPath);
        String iso = escape(isoPath);
        String command = floppyPath == null ? "boot -l c" :
                "boot \"" + escape(floppyPath) + "\"";
        return "[dosbox]\nmemsize=" + memoryMb + "\n[cpu]\ncore=" + cpuCore +
                cycleLine(fixedCycles) +
                "\ncputype=pentium_slow" +
                "\n[mixer]\nnosound=" + (!soundEnabled) +
                "\n[sblaster]\nsbtype=sb16\nsbbase=220\nirq=7\ndma=1\nhdma=5" +
                "\n[autoexec]\n@echo off\n" +
                "imgmount 2 \"" + disk + "\" -t hdd -fs none -size " +
                WindowsInstallMedia.DISK_GEOMETRY + "\n" +
                "imgmount d \"" + iso + "\" -t iso\n" +
                ":robowindows_boot\n" + command + "\n" +
                "goto robowindows_boot\n";
    }

    private static void validate(int memoryMb, String cpuCore) {
        if (memoryMb != 16 && memoryMb != 32 && memoryMb != 64 && memoryMb != 128) {
            throw new IllegalArgumentException("Unsupported memory size");
        }
        if (!cpuCore.equals("auto") && !cpuCore.equals("normal") &&
                !cpuCore.equals("dynamic")) {
            throw new IllegalArgumentException("Unsupported CPU core");
        }
    }

    private static void validateCycles(String cpuCore, int fixedCycles) {
        if (fixedCycles < 0) throw new IllegalArgumentException("Invalid fixed cycles");
        if (fixedCycles > 0 && !cpuCore.equals("normal")) {
            throw new IllegalArgumentException("Fixed cycles require the normal CPU core");
        }
    }

    private static String cycleLine(int fixedCycles) {
        return fixedCycles == 0 ? "" : "\ncycles=fixed " + fixedCycles;
    }

    private static String escape(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
