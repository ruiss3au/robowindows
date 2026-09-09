package org.robowindows.app;

public final class SettingsDraftTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        SettingsDraft d = new SettingsDraft("Windows", 64, 12000, true, false, "normal");
        check(!d.dirty());
        check(d.presentationMode == PresentationPolicy.SOFTWARE);
        d.presentationMode = PresentationPolicy.GPU; check(d.dirty());
        d.dynamic = true; check(d.presentationMode == PresentationPolicy.GPU);
        d.dynamic = false; d.presentationMode = PresentationPolicy.SOFTWARE; check(!d.dirty());
        check(PresentationPolicy.forLaunch(1, true, true, false) == 1);
        check(PresentationPolicy.forLaunch(1, false, true, false) == 0);
        check(PresentationPolicy.forLaunch(1, true, false, false) == 0);
        check(PresentationPolicy.forLaunch(1, true, true, true) == 0);
        check(!PresentationPolicy.allowed(-1) && !PresentationPolicy.allowed(2));
        try { PresentationPolicy.forLaunch(9, true, true, false); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { /* rejected */ }
        d.name = "New name"; check(d.dirty());
        d.name = "Windows"; check(!d.dirty());
        check(SettingsDraft.validatedName("  win98 dynrec exp  ").equals("win98 dynrec exp"));
        check(SettingsDraft.validatedName("\u00a0Windows\u2003").equals("Windows"));
        check(SettingsDraft.validatedName("../name / label").equals("../name / label"));
        check(SettingsDraft.validatedName("é中😀").equals("é中😀"));
        String limit = "😀".repeat(64);
        check(SettingsDraft.validatedName(limit).equals(limit));
        for (String invalid : new String[]{null, "", "  ", "\u00a0", "x\ny", "x\ty", "x\u0000y",
                "x\u202ey", "x\u200by", "\ud800", limit + "x"}) {
            boolean rejected = false;
            try { SettingsDraft.validatedName(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected);
        }
        check(!d.dirty());
        d.dynamic = true; check(d.dirty() && d.normalCycles == 12000);
        d.dynamic = false; check(!d.dirty());
        d.sound = false; check(d.dirty()); d.sound = true;
        d.memoryMb = 16; check(d.dirty()); d.memoryMb = 64;
        d.normalCycles = 20000; check(d.dirty());
        // Applying uses a new saved baseline; cancel simply discards the old draft.
        SettingsDraft saved = new SettingsDraft(d.name, d.memoryMb, d.normalCycles, d.sound, d.dynamic, d.normalCore);
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
