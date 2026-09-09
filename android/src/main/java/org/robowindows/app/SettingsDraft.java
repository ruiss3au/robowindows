package org.robowindows.app;

/** Unpublished properties. No Android, disk, or runtime side effects. */
final class SettingsDraft {
    int memoryMb;
    int normalCycles;
    boolean sound;
    boolean dynamic;
    String normalCore;
    private final String savedCore;
    private final int savedMemory, savedCycles;
    private final boolean savedSound, savedDynamic;

    SettingsDraft(int memory, int cycles, boolean sound, boolean dynamic, String core) {
        memoryMb = savedMemory = memory;
        normalCycles = savedCycles = cycles;
        this.sound = savedSound = sound;
        this.dynamic = savedDynamic = dynamic;
        normalCore = savedCore = core;
    }

    boolean dirty() {
        return memoryMb != savedMemory || normalCycles != savedCycles ||
                sound != savedSound || dynamic != savedDynamic || !normalCore.equals(savedCore);
    }

    static String dynamicUnavailable(boolean debug, boolean experimental,
            boolean busy, boolean recovery, boolean clean, boolean cpuPassed) {
        if (!experimental) return "DynRec requires an experimental copy.";
        if (!debug) return "DynRec is available only in diagnostic builds.";
        if (busy) return "Stop the current session or test first.";
        if (recovery) return "Complete this copy's disk-check recovery first.";
        if (!clean) return "Shut down this copy normally inside Windows first.";
        if (!cpuPassed) return "Run and pass the CPU test in Tests for this build.";
        return null;
    }
}
