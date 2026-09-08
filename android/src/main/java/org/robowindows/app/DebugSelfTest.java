package org.robowindows.app;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

final class DebugSelfTest {
    private static final String TAG = "RoboWindowsTest";

    private DebugSelfTest() {}

    static void run(Context context) {
        File directory = new File(context.getFilesDir(), "machines/debug-recovery-probe");
        File interrupted = new File(directory, "orphan.part");
        File testRoot = new File(context.getCacheDir(), "debug-machine-store");
        try {
            if (!directory.mkdirs() && !directory.isDirectory()) throw new Exception("mkdir");
            try (FileOutputStream output = new FileOutputStream(interrupted)) {
                output.write(1);
                output.getFD().sync();
            }
            new MachineStore(context);
            require(!interrupted.exists(), "interrupted import cleanup");
            File launch = new File(directory, "launch.conf");
            MachineStore.writeLaunchConfig(launch, new File(directory, "disk.img"),
                    "img", 16, "normal", false);
            require(launch.isFile(), "atomic launch configuration");
            require(!new File(directory, "launch.conf.part").exists(), "launch staging cleanup");

            ArrayList<MediaAsset> assets = new ArrayList<>();
            assets.add(new MediaAsset("tools.iso", "/source.iso", "/source.iso",
                    "0123456789abcdef", 123));
            MachineProfile profile = new MachineProfile("id", "DOS tools", "DOS", "disk.img",
                    "/source.img", "/disk.img", "abcdef", "/launch.conf", 16, "normal",
                    false, 10, 20, assets);
            MachineProfile decoded = MachineProfile.fromJson(profile.toJson());
            require(decoded.mediaAssets.size() == 1, "media asset round trip");
            require(decoded.memoryMb == 16 && !decoded.soundEnabled, "configuration round trip");

            JSONObject legacy = profile.toJson();
            legacy.put("schemaVersion", 1);
            legacy.remove("runtimePath");
            legacy.remove("launchPath");
            legacy.remove("mediaAssets");
            MachineProfile migrated = MachineProfile.fromJson(legacy);
            require(migrated.runtimePath.equals(migrated.mediaPath), "legacy runtime migration");
            require(migrated.mediaAssets.isEmpty(), "legacy media migration");

            deleteTree(testRoot);
            require(testRoot.mkdirs(), "test store root");
            File diskInput = new File(testRoot, "probe.img");
            writeBytes(diskInput, new byte[]{1, 2, 3, 4});
            MachineStore isolated = new MachineStore(context, new File(testRoot, "private"),
                    "machine_store_debug_probe");
            MachineProfile imported = isolated.importMachine(Uri.fromFile(diskInput), "DOS");
            require(imported.memoryMb == 16 && imported.cpuCore.equals("normal"),
                    "DOS creation defaults");
            require(new File(imported.mediaPath).isFile() &&
                    !new File(imported.mediaPath).canWrite(), "immutable imported source");
            require(new File(imported.runtimePath).isFile() &&
                    !imported.runtimePath.equals(imported.mediaPath), "writable runtime clone");
            try (RandomAccessFile runtime = new RandomAccessFile(imported.runtimePath, "rw")) {
                runtime.seek(0); runtime.write(9);
            }
            require(readFirstByte(new File(imported.mediaPath)) == 1,
                    "source and runtime isolation");
            String config = readText(new File(imported.launchPath));
            require(config.contains("boot \"" + imported.runtimePath + "\""),
                    "generated direct-boot config");
            MachineProfile configured = isolated.updateConfiguration(imported, 64, "auto", true);
            MachineProfile configuredReloaded = isolated.load().get(0);
            require(configured.memoryMb == 64 && configured.soundEnabled &&
                    configuredReloaded.memoryMb == 64 && configuredReloaded.cpuCore.equals("auto"),
                    "persisted configuration update");
            isolated.markGuestShutdown(configured.id);
            ArrayList<String> copyStages = new ArrayList<>();
            long[] copyProgress = {0, 0};
            MachineProfile experimental = isolated.createExperimentalCopy(configured,
                    (stage, completed, total) -> {
                        if (!stage.isEmpty()) copyStages.add(stage);
                        require(total > 0 && completed >= copyProgress[0] && completed <= total,
                                "monotonic copy progress");
                        copyProgress[0] = completed;
                        copyProgress[1] = total;
                    });
            require(experimental.isExperimental() && experimental.name.equals("probe - copy"),
                    "experimental copy identity");
            require(copyStages.contains("Verifying source disk") &&
                    copyStages.contains("Copying disk") &&
                    copyStages.contains("Verifying copied disk") &&
                    copyStages.contains("Finalizing machine") && copyProgress[0] == copyProgress[1],
                    "copy progress stages");
            require(!experimental.runtimePath.equals(configured.runtimePath) &&
                    new File(experimental.runtimePath).isFile(), "independent experimental disk");
            require(readFirstByte(new File(experimental.runtimePath)) ==
                    readFirstByte(new File(configured.runtimePath)), "experimental copy checksum content");
            try (RandomAccessFile experimentalDisk = new RandomAccessFile(experimental.runtimePath, "rw")) {
                experimentalDisk.seek(0); experimentalDisk.write(7);
            }
            require(readFirstByte(new File(configured.runtimePath)) == 9,
                    "experimental disk cannot modify stable runtime");
            MachineProfile tuned = isolated.updatePerformanceProfile(experimental, 30000);
            require(tuned.fixedCycles == 30000 && tuned.cpuCore.equals("normal") &&
                    readText(new File(tuned.launchPath)).contains("cycles=fixed 30000"),
                    "fixed-cycle normal performance profile");
            isolated.markSessionStarted(tuned.id);
            require(isolated.recoverInterruptedExperimental(), "experimental recovery applied");
            MachineProfile recovered = isolated.load().get(1);
            require(recovered.fixedCycles == MachineStore.SAFE_EXPERIMENTAL_CYCLES &&
                    readText(new File(recovered.launchPath)).contains("cycles=fixed 12000"),
                    "experimental recovery returns safe profile");
            isolated.markSessionStopped();
            isolated.markGuestShutdown(recovered.id);
            MachineProfile dynamic = isolated.selectDynamicProfile(recovered);
            require(dynamic.isDynamicSelected() && dynamic.configurationGeneration >
                    recovered.configurationGeneration, "dynamic selection is persisted only");
            DynamicAttempt attempt = isolated.prepareDynamicStart(dynamic);
            String journalText = readText(new File(new File(dynamic.runtimePath).getParentFile(),
                    "dynamic-attempt.json"));
            require(attempt.state.equals(DynamicAttempt.EXECUTING) &&
                    readText(new File(dynamic.launchPath)).contains("core=dynamic") &&
                    readText(new File(dynamic.launchPath)).contains("cycles=fixed 20000"),
                    "dynamic handoff is journaled before native execution");
            require(!journalText.contains(dynamic.runtimePath) &&
                    !journalText.contains(dynamic.mediaPath) &&
                    !journalText.contains(dynamic.launchPath) &&
                    !journalText.contains(dynamic.mediaName),
                    "dynamic journal contains no private media paths or names");
            require(isolated.validateDynamicChildHandoff(dynamic.id, attempt.attemptId,
                    attempt.generation).id.equals(dynamic.id),
                    "dynamic child handoff binds to the durable attempt");
            boolean rejectedStaleAttempt = false;
            try {
                isolated.validateDynamicChildHandoff(dynamic.id, "stale-attempt", attempt.generation);
            } catch (IOException expected) {
                rejectedStaleAttempt = true;
            }
            require(rejectedStaleAttempt, "dynamic child rejects a stale attempt identity");
            require(isolated.recoverDynamicAttempts(), "unfinished dynamic handoff is recovered");
            MachineProfile dynamicRecovered = isolated.load().get(1);
            require(!dynamicRecovered.isDynamicSelected() &&
                    isolated.requiresDynamicMediaCheck(dynamicRecovered) &&
                    readText(new File(dynamicRecovered.launchPath)).contains("core=normal"),
                    "dynamic recovery restores normal and quarantines disk");
            File experimentalDirectory = new File(recovered.runtimePath).getParentFile();
            isolated.deleteMachine(dynamicRecovered);
            require(!experimentalDirectory.exists() && isolated.load().size() == 1 &&
                    isolated.load().get(0).id.equals(configured.id),
                    "selected machine deletion preserves stable source");
            File windowsInput = new File(testRoot, "windows.img");
            writeBytes(windowsInput, new byte[]{9, 8, 7, 6});
            MachineProfile windows = isolated.importMachine(Uri.fromFile(windowsInput), "Windows");
            require(windows.memoryMb == 64 && windows.cpuCore.equals("normal"),
                    "Windows creation defaults");
            isolated.markSessionStarted(imported.id);
            require(isolated.hasInterruptedSession(), "interrupted session marker");
            isolated.markSessionStopped();
            require(!isolated.hasInterruptedSession(), "orderly session marker cleanup");
            File isoInput = new File(testRoot, "tools.iso");
            writeBytes(isoInput, new byte[]{5, 6, 7, 8});
            isolated.importAdditionalMedia(Uri.fromFile(isoInput), imported.id);
            MachineProfile reloaded = isolated.load().get(0);
            require(reloaded.mediaAssets.size() == 1 &&
                    reloaded.mediaAssets.get(0).name.equals("tools.iso") &&
                    !reloaded.mediaAssets.get(0).sha256.isEmpty(), "attached ISO provenance");
            context.getSharedPreferences("machine_store_debug_probe", Context.MODE_PRIVATE)
                    .edit().clear().commit();
            deleteTree(testRoot);

            NativeHost.pushKey(0, 29, 30, 2, 65, 257, 7, 123000000L);
            String key = NativeHost.lastInputEvent();
            require(key.contains("\"scanCode\":30") && key.contains("\"repeat\":2") &&
                    key.contains("\"meta\":65") && key.contains("\"device\":7"),
                    "key event serialization");
            NativeHost.pushMouse(2, 1.25f, -2.5f, 100, 200, 7, 2,
                    -1, 0.5f, 8194, 8, 456000000L, true);
            String mouse = NativeHost.lastInputEvent();
            require(mouse.contains("\"relativeX\":1.250") &&
                    mouse.contains("\"horizontalScroll\":0.500") &&
                    mouse.contains("\"captured\":true"), "mouse event serialization");
            NativeHost.pushTouch(0, 2, 12, 34, 0.75f, 4098, 9, 789000000L);
            String touch = NativeHost.lastInputEvent();
            require(touch.contains("\"pointerCount\":2") &&
                    touch.contains("\"pressure\":0.750"), "touch event serialization");
            NativeHost.cancelInput();
            require(NativeHost.lastInputEvent().contains("\"type\":\"cancel\""),
                    "cancel serialization");
            for (int i = 0; i < 4100; i++) {
                NativeHost.pushTouch(2, 1, i, i, 0.5f, 4098, 9, 800000000L + i);
            }
            JSONObject stats = new JSONObject(NativeHost.inputStats());
            require(stats.getInt("queued") == 4096 && stats.getLong("dropped") > 0,
                    "bounded input history");
            NativeHost.cancelInput();
            Log.i(TAG, "persistence and input bridge probes passed");
        } catch (Exception error) {
            Log.e(TAG, "persistence probe failed", error);
        } finally {
            interrupted.delete();
            new File(directory, "launch.conf").delete();
            directory.delete();
            context.getSharedPreferences("machine_store_debug_probe", Context.MODE_PRIVATE)
                    .edit().clear().commit();
            deleteTree(testRoot);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }

    private static int readFirstByte(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) { return input.read(); }
    }

    private static String readText(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
