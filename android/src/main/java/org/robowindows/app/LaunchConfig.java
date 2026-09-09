package org.robowindows.app;

final class LaunchConfig {
    static final int DYNAMIC_EXPERIMENTAL_CYCLES = 20000;
    static final int DYNAMIC_PERFORMANCE_CYCLES = 30000;
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
        if (fixedCycles > 0 && !cpuCore.equals("normal") &&
                !(cpuCore.equals("dynamic") && isDynamicDiagnosticCycles(fixedCycles))) {
            throw new IllegalArgumentException("Fixed cycles require the named experimental policy");
        }
    }

    static boolean isDynamicDiagnosticCycles(int fixedCycles) {
        return fixedCycles == DYNAMIC_EXPERIMENTAL_CYCLES ||
                fixedCycles == DYNAMIC_PERFORMANCE_CYCLES;
    }

    static String createDynamic(String mediaPath, String extension, int memoryMb,
            DynamicCyclePolicy policy, boolean soundEnabled) {
        return applyDynamicCyclePolicy(create(mediaPath, extension, memoryMb, "dynamic", 0,
                soundEnabled), policy);
    }

    static String createWindowsDynamic(String diskPath, String isoPath, String bootFloppyPath,
            boolean bootInstaller, int memoryMb, DynamicCyclePolicy policy,
            boolean soundEnabled) {
        return applyDynamicCyclePolicy(createWindowsInstall(diskPath, isoPath, bootFloppyPath,
                bootInstaller, memoryMb, "dynamic", 0, soundEnabled), policy);
    }

    private static String applyDynamicCyclePolicy(String config, DynamicCyclePolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Dynamic cycle policy is required");
        String marker = "\ncore=dynamic\n";
        int first = config.indexOf(marker);
        if (first < 0 || config.indexOf(marker, first + marker.length()) >= 0 ||
                config.contains("\ncycles=")) {
            throw new IllegalArgumentException("Dynamic configuration cannot accept cycle policy");
        }
        return config.substring(0, first + marker.length()) + "cycles=" + policy.configValue +
                "\n" + config.substring(first + marker.length());
    }

    private static String cycleLine(int fixedCycles) {
        return fixedCycles == 0 ? "" : "\ncycles=fixed " + fixedCycles;
    }

    private static String escape(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
