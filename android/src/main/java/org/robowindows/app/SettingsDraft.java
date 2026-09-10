package org.robowindows.app;

/** Unpublished properties. No Android, disk, or runtime side effects. */
final class SettingsDraft {
    String name;
    int presentationMode;
    private final int savedPresentation;
    private final String savedName;
    int memoryMb;
    int normalCycles;
    boolean sound;
    boolean dynamic;
    String normalCore;
    private final String savedCore;
    private final int savedMemory, savedCycles;
    private final boolean savedSound, savedDynamic;

    SettingsDraft(String name, int memory, int cycles, boolean sound, boolean dynamic, String core) {
        this(name, memory, cycles, sound, dynamic, core, PresentationPolicy.SOFTWARE);
    }

    SettingsDraft(String name, int memory, int cycles, boolean sound, boolean dynamic, String core,
            int presentationMode) {
        this.presentationMode = savedPresentation = presentationMode;
        this.name = savedName = name;
        memoryMb = savedMemory = memory;
        normalCycles = savedCycles = cycles;
        this.sound = savedSound = sound;
        this.dynamic = savedDynamic = dynamic;
        normalCore = savedCore = core;
    }

    boolean dirty() {
        return presentationMode != savedPresentation || !java.util.Objects.equals(name, savedName) || memoryMb != savedMemory || normalCycles != savedCycles ||
                sound != savedSound || dynamic != savedDynamic || !normalCore.equals(savedCore);
    }

    static String validatedName(String name) {
        if (name == null) throw new IllegalArgumentException("Enter a machine name.");
        for (int i = 0; i < name.length();) {
            int cp = name.codePointAt(i);
            int type = Character.getType(cp);
            if (Character.isISOControl(cp) || type == Character.FORMAT || type == Character.SURROGATE) {
                throw new IllegalArgumentException("Machine names cannot contain control or hidden formatting characters.");
            }
            i += Character.charCount(cp);
        }
        int start = 0, end = name.length();
        while (start < end && space(name.codePointAt(start))) start += Character.charCount(name.codePointAt(start));
        while (end > start && space(name.codePointBefore(end))) end -= Character.charCount(name.codePointBefore(end));
        String trimmed = name.substring(start, end);
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Enter a machine name.");
        if (trimmed.codePointCount(0, trimmed.length()) > 64) {
            throw new IllegalArgumentException("Keep the machine name to 64 characters or fewer.");
        }
        return trimmed;
    }

    private static boolean space(int cp) { return Character.isWhitespace(cp) || Character.isSpaceChar(cp); }

    static String dynamicUnavailable(boolean debug, boolean experimental,
            boolean busy, boolean recovery, boolean clean, boolean cpuPassed) {
        if (busy) return "Stop the current session or test first.";
        if (recovery) return "Complete this machine's disk-check recovery first.";
        if (!clean) return "Shut down this machine normally inside Windows first.";
        if (!cpuPassed) return "Run and pass the CPU test in Tests for this build.";
        return null;
    }
}
