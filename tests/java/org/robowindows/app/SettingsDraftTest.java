package org.robowindows.app;

public final class SettingsDraftTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        SettingsDraft d = new SettingsDraft(64, 12000, true, false, "normal");
        check(!d.dirty());
        d.dynamic = true; check(d.dirty() && d.normalCycles == 12000);
        d.dynamic = false; check(!d.dirty());
        d.sound = false; check(d.dirty()); d.sound = true;
        d.memoryMb = 16; check(d.dirty()); d.memoryMb = 64;
        d.normalCycles = 20000; check(d.dirty());
        // Applying uses a new saved baseline; cancel simply discards the old draft.
        SettingsDraft saved = new SettingsDraft(d.memoryMb, d.normalCycles, d.sound, d.dynamic, d.normalCore);
        check(!saved.dirty());
        check(SettingsDraft.dynamicUnavailable(true, true, false, false, true, true) == null);
        check(SettingsDraft.dynamicUnavailable(false, true, false, false, true, true) != null);
        check(SettingsDraft.dynamicUnavailable(true, false, false, false, true, true) != null);
        check(SettingsDraft.dynamicUnavailable(true, true, true, false, true, true) != null);
        check(SettingsDraft.dynamicUnavailable(true, true, false, true, true, true) != null);
        check(SettingsDraft.dynamicUnavailable(true, true, false, false, false, true) != null);
        check(SettingsDraft.dynamicUnavailable(true, true, false, false, true, false) != null);
        String config = DisposableCoreConfig.expected("/fixture/test.img");
        check(config.contains("core=normal\n") && config.endsWith("boot \"/fixture/test.img\"\n"));
        for (String invalid : new String[]{"bad\"path", "bad\npath", "bad\rpath"}) {
            boolean rejected = false;
            try { DisposableCoreConfig.expected(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected);
        }
        System.out.println("Settings draft and eligibility tests passed");
    }
}
