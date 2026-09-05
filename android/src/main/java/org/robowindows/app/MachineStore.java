package org.robowindows.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

final class MachineStore {
    private static final String PREFS = "machine_store";
    private static final String KEY = "profiles";
    private static final String ACTIVE_SESSION = "active_session";
    private final Context context;
    private final File filesRoot;
    private final String preferencesName;

    MachineStore(Context context) {
        this(context, context.getFilesDir(), PREFS);
    }

    MachineStore(Context context, File filesRoot, String preferencesName) {
        this.context = context.getApplicationContext();
        this.filesRoot = filesRoot;
        this.preferencesName = preferencesName;
        removeInterruptedImports(new File(filesRoot, "machines"));
        new File(filesRoot, "system").mkdirs();
        new File(filesRoot, "saves").mkdirs();
    }

    List<MachineProfile> load() {
        ArrayList<MachineProfile> profiles = new ArrayList<>();
        boolean migrated = false;
        String encoded = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                try {
                    MachineProfile profile = MachineProfile.fromJson(array.getJSONObject(i));
                    File launch = new File(profile.launchPath);
                    if (!launch.isFile()) {
                        String extension = extension(profile.mediaName);
                        File source = new File(profile.mediaPath);
                        File runtime = source;
                        if (isWindowsInstaller(profile)) {
                            runtime = new File(profile.runtimePath);
                            File bootFloppy = windowsBootFloppy(profile);
                            if (!source.isFile() || !runtime.isFile() || !bootFloppy.isFile()) {
                                throw new IOException("Windows installation media is incomplete");
                            }
                            writeWindowsLaunchConfig(launch, runtime, source, bootFloppy, true,
                                    profile.memoryMb, profile.cpuCore, profile.soundEnabled);
                        } else if (!extension.equals("iso") && !extension.equals("cue")) {
                            runtime = new File(source.getParentFile(), "disk." + extension);
                            if (!runtime.isFile()) copyFile(source, runtime);
                            writeLaunchConfig(launch, runtime, extension, profile.memoryMb,
                                    profile.cpuCore, profile.soundEnabled);
                        } else {
                            throw new IOException("Standalone ISO profile is unsupported");
                        }
                        profile = new MachineProfile(profile.id, profile.name, profile.family,
                                profile.mediaName, profile.mediaPath, runtime.getAbsolutePath(),
                                profile.mediaSha256, launch.getAbsolutePath(), profile.memoryMb,
                                profile.cpuCore, profile.soundEnabled, profile.createdAt,
                                profile.lastBootedAt, profile.mediaAssets);
                        migrated = true;
                    }
                    profiles.add(profile);
                } catch (JSONException ignored) {
                    // A damaged or newer individual profile cannot poison the library.
                } catch (IOException ignored) {
                    // A profile with unavailable media remains excluded until recovery succeeds.
                }
            }
        } catch (JSONException ignored) {
            // Treat a damaged index as an empty recoverable library.
        }
        if (migrated) save(profiles);
        return profiles;
    }

    MachineProfile importMachine(Uri source, String family) throws IOException {
        String id = UUID.randomUUID().toString();
        String displayName = queryDisplayName(source);
        String extension = extension(displayName);
        if (extension.equals("iso") && "Windows".equals(family)) {
            return importWindowsInstaller(source, id, displayName);
        }
        if (!isBootMedia(extension)) {
            if (LaunchConfig.supportsMedia(extension)) {
                throw new IOException("Create the machine from an IMG, IMA, or VHD, then attach the ISO while it is running.");
            }
            throw new IOException("Choose an IMG, IMA, or VHD disk image.");
        }
        File directory = new File(filesRoot, "machines/" + id);
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IOException("Cannot create machine storage");
        long sourceBytes = querySize(source);
        long requiredBytes = sourceBytes < 0 ? -1 : sourceBytes *
                ((extension.equals("iso") || extension.equals("cue")) ? 1L : 2L);
        if (requiredBytes >= 0 && directory.getUsableSpace() < requiredBytes) {
            directory.delete();
            throw new IOException("Not enough storage space");
        }
        File staged = new File(directory, "media.part");
        File imported = new File(directory, "source." + extension);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(staged)) {
            if (input == null) throw new IOException("Cannot open selected media");
            byte[] buffer = new byte[128 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
            output.getFD().sync();
        } catch (IOException error) {
            staged.delete();
            throw error;
        }
        if (!staged.renameTo(imported)) {
            staged.delete();
            throw new IOException("Cannot finalize imported media");
        }
        if (!imported.setReadOnly()) {
            imported.delete();
            directory.delete();
            throw new IOException("Cannot protect imported source media");
        }
        File runtime = imported;
        if (!extension.equals("iso") && !extension.equals("cue")) {
            runtime = new File(directory, "disk." + extension);
            try {
                copyFile(imported, runtime);
            } catch (IOException error) {
                imported.delete();
                directory.delete();
                throw error;
            }
        }
        File launch = new File(directory, "launch.conf");
        boolean dosFamily = "DOS".equals(family);
        int memoryMb = dosFamily ? 16 : 64;
        String cpuCore = "normal";
        try {
            writeLaunchConfig(launch, runtime, extension, memoryMb, cpuCore, true);
        } catch (IOException error) {
            runtime.delete();
            imported.delete();
            directory.delete();
            throw error;
        }
        long now = System.currentTimeMillis();
        MachineProfile profile = new MachineProfile(id, baseName(displayName), family,
                displayName, imported.getAbsolutePath(), runtime.getAbsolutePath(), hex(digest.digest()),
                launch.getAbsolutePath(), memoryMb, cpuCore, true, now, 0);
        List<MachineProfile> profiles = load();
        profiles.add(profile);
        if (!save(profiles)) {
            launch.delete();
            if (!runtime.equals(imported)) runtime.delete();
            imported.delete();
            directory.delete();
            throw new IOException("Cannot save machine profile");
        }
        return profile;
    }

    private MachineProfile importWindowsInstaller(Uri source, String id, String displayName)
            throws IOException {
        File directory = new File(filesRoot, "machines/" + id);
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create machine storage");
        }
        long sourceBytes = querySize(source);
        long requiredBytes = sourceBytes < 0 ? WindowsInstallMedia.DISK_BYTES :
                sourceBytes + WindowsInstallMedia.DISK_BYTES + 2_949_120L;
        if (directory.getUsableSpace() < requiredBytes) {
            directory.delete();
            throw new IOException("Not enough storage space for a 2 GB Windows disk");
        }
        File staged = new File(directory, "media.part");
        File iso = new File(directory, "source.iso");
        File bootFloppy = new File(directory, "cdboot.img");
        File disk = new File(directory, "disk.img");
        File launch = new File(directory, "launch.conf");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(staged)) {
                if (input == null) throw new IOException("Cannot open selected ISO");
                byte[] buffer = new byte[128 * 1024];
                for (int read; (read = input.read(buffer)) != -1;) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                output.getFD().sync();
            }
            if (!staged.renameTo(iso)) throw new IOException("Cannot finalize imported ISO");
            WindowsInstallMedia.extractElToritoFloppy(iso, bootFloppy);
            WindowsInstallMedia.createBlankDisk(disk);
            if (!iso.setReadOnly()) throw new IOException("Cannot protect imported ISO");
            writeWindowsLaunchConfig(launch, disk, iso, bootFloppy, true, 64, "normal", true);
        } catch (IOException error) {
            staged.delete(); launch.delete(); disk.delete(); bootFloppy.delete(); iso.delete();
            directory.delete();
            throw error;
        }
        long now = System.currentTimeMillis();
        MachineProfile profile = new MachineProfile(id, baseName(displayName), "Windows",
                displayName, iso.getAbsolutePath(), disk.getAbsolutePath(), hex(digest.digest()),
                launch.getAbsolutePath(), 64, "normal", true, now, 0);
        List<MachineProfile> profiles = load();
        profiles.add(profile);
        if (!save(profiles)) {
            launch.delete(); disk.delete(); bootFloppy.delete(); iso.delete(); directory.delete();
            throw new IOException("Cannot save machine profile");
        }
        return profile;
    }

    File importAdditionalMedia(Uri source, String machineId) throws IOException {
        String displayName = queryDisplayName(source);
        String extension = extension(displayName);
        if (!LaunchConfig.supportsMedia(extension)) throw new IOException("Unsupported media");
        File directory = new File(filesRoot, "machines/" + machineId + "/media");
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IOException("Cannot create media storage");
        long sourceBytes = querySize(source);
        long requiredBytes = sourceBytes < 0 ? -1 : sourceBytes *
                ((extension.equals("iso") || extension.equals("cue")) ? 1L : 2L);
        if (requiredBytes >= 0 && directory.getUsableSpace() < requiredBytes) {
            throw new IOException("Not enough storage space");
        }
        String id = UUID.randomUUID().toString();
        File staged = new File(directory, id + ".part");
        File imported = new File(directory, id + "-source." + extension);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(staged)) {
            if (input == null) throw new IOException("Cannot open selected media");
            byte[] buffer = new byte[128 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
            output.getFD().sync();
        } catch (IOException error) {
            staged.delete();
            throw error;
        }
        if (!staged.renameTo(imported)) {
            staged.delete();
            throw new IOException("Cannot finalize media");
        }
        if (!imported.setReadOnly()) {
            imported.delete();
            throw new IOException("Cannot protect imported source media");
        }
        File runtime = imported;
        if (!extension.equals("iso") && !extension.equals("cue")) {
            runtime = new File(directory, id + "-disk." + extension);
            try {
                copyFile(imported, runtime);
            } catch (IOException error) {
                imported.delete();
                throw error;
            }
        }
        try {
            recordMedia(machineId, new MediaAsset(displayName, imported.getAbsolutePath(),
                    runtime.getAbsolutePath(), hex(digest.digest()), System.currentTimeMillis()));
        } catch (IOException error) {
            if (!runtime.equals(imported)) runtime.delete();
            imported.delete();
            throw error;
        }
        return runtime;
    }

    void markBooted(MachineProfile selected) {
        List<MachineProfile> profiles = load();
        for (int i = 0; i < profiles.size(); i++) {
                MachineProfile profile = profiles.get(i);
            if (profile.id.equals(selected.id)) {
                profiles.set(i, new MachineProfile(profile.id, profile.name, profile.family,
                        profile.mediaName, profile.mediaPath, profile.runtimePath, profile.mediaSha256,
                        profile.launchPath, profile.memoryMb, profile.cpuCore,
                        profile.soundEnabled, profile.createdAt, System.currentTimeMillis(),
                        profile.mediaAssets));
                break;
            }
        }
        save(profiles);
    }

    void markSessionStarted(String machineId) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_SESSION, machineId).commit();
    }

    void markSessionStopped() {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
                .remove(ACTIVE_SESSION).commit();
    }

    boolean hasInterruptedSession() {
        return context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .contains(ACTIVE_SESSION);
    }

    void acknowledgeRecovery() {
        markSessionStopped();
    }

    MachineProfile updateConfiguration(MachineProfile selected, int memoryMb, String cpuCore,
            boolean soundEnabled) throws IOException {
        String extension = extension(selected.mediaName);
        if (isWindowsInstaller(selected)) {
            writeWindowsLaunchConfig(new File(selected.launchPath), new File(selected.runtimePath),
                    new File(selected.mediaPath), windowsBootFloppy(selected),
                    bootsInstaller(selected), memoryMb, cpuCore, soundEnabled);
        } else {
            writeLaunchConfig(new File(selected.launchPath), new File(selected.runtimePath), extension,
                    memoryMb, cpuCore, soundEnabled);
        }
        MachineProfile updated = new MachineProfile(selected.id, selected.name, selected.family,
                selected.mediaName, selected.mediaPath, selected.runtimePath, selected.mediaSha256, selected.launchPath,
                memoryMb, cpuCore, soundEnabled, selected.createdAt, selected.lastBootedAt,
                selected.mediaAssets);
        List<MachineProfile> profiles = load();
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(selected.id)) {
                profiles.set(i, updated);
                break;
            }
        }
        if (!save(profiles)) {
            if (isWindowsInstaller(selected)) {
                writeWindowsLaunchConfig(new File(selected.launchPath),
                        new File(selected.runtimePath), new File(selected.mediaPath),
                        windowsBootFloppy(selected), bootsInstaller(selected), selected.memoryMb,
                        selected.cpuCore, selected.soundEnabled);
            } else {
                writeLaunchConfig(new File(selected.launchPath), new File(selected.runtimePath),
                        extension, selected.memoryMb, selected.cpuCore, selected.soundEnabled);
            }
            throw new IOException("Cannot save machine settings");
        }
        return updated;
    }

    boolean isWindowsInstaller(MachineProfile profile) {
        return "Windows".equals(profile.family) && extension(profile.mediaName).equals("iso") &&
                new File(profile.runtimePath).getName().equals("disk.img");
    }

    boolean bootsInstaller(MachineProfile profile) {
        if (!isWindowsInstaller(profile)) return false;
        try {
            byte[] data = java.nio.file.Files.readAllBytes(new File(profile.launchPath).toPath());
            return new String(data, StandardCharsets.UTF_8).contains("boot \"");
        } catch (IOException ignored) {
            return true;
        }
    }

    void setWindowsInstallerBoot(MachineProfile profile, boolean installer) throws IOException {
        if (!isWindowsInstaller(profile)) throw new IOException("Not a Windows installer profile");
        writeWindowsLaunchConfig(new File(profile.launchPath), new File(profile.runtimePath),
                new File(profile.mediaPath), windowsBootFloppy(profile), installer,
                profile.memoryMb, profile.cpuCore, profile.soundEnabled);
    }

    void setWindowsUtilityBoot(MachineProfile profile, File utilityDisk) throws IOException {
        if (!isWindowsInstaller(profile)) throw new IOException("Not a Windows installer profile");
        String extension = extension(utilityDisk.getName());
        if (!LaunchConfig.supportsBoot(extension) || !utilityDisk.isFile()) {
            throw new IOException("Choose an IMG, IMA, or VHD utility disk");
        }
        writeConfigAtomically(new File(profile.launchPath), LaunchConfig.createWindowsUtility(
                profile.runtimePath, profile.mediaPath, utilityDisk.getAbsolutePath(),
                profile.memoryMb, profile.cpuCore, profile.soundEnabled));
    }

    private static File windowsBootFloppy(MachineProfile profile) {
        return new File(new File(profile.mediaPath).getParentFile(), "cdboot.img");
    }

    private void recordMedia(String machineId, MediaAsset asset) throws IOException {
        List<MachineProfile> profiles = load();
        for (int i = 0; i < profiles.size(); i++) {
            MachineProfile profile = profiles.get(i);
            if (!profile.id.equals(machineId)) continue;
            ArrayList<MediaAsset> assets = new ArrayList<>(profile.mediaAssets);
            assets.add(asset);
            profiles.set(i, new MachineProfile(profile.id, profile.name, profile.family,
                    profile.mediaName, profile.mediaPath, profile.runtimePath, profile.mediaSha256,
                    profile.launchPath, profile.memoryMb, profile.cpuCore, profile.soundEnabled,
                    profile.createdAt, profile.lastBootedAt, assets));
            if (!save(profiles)) throw new IOException("Cannot save media metadata");
            return;
        }
        throw new IOException("Machine profile is unavailable");
    }

    private boolean save(List<MachineProfile> profiles) {
        JSONArray array = new JSONArray();
        for (MachineProfile profile : profiles) {
            try { array.put(profile.toJson()); } catch (JSONException ignored) {}
        }
        return context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
                .putString(KEY, array.toString()).commit();
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        } catch (RuntimeException ignored) {
            // Providers may omit or reject size metadata; streaming remains authoritative.
        }
        return -1;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {
            // File-backed debug probes and some providers do not implement metadata queries.
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "Machine" : segment;
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isBootMedia(String extension) {
        return LaunchConfig.supportsBoot(extension);
    }

    static void writeLaunchConfig(File launch, File media, String extension, int memoryMb,
            String cpuCore, boolean soundEnabled) throws IOException {
        String config = LaunchConfig.create(media.getAbsolutePath(), extension, memoryMb,
                cpuCore, soundEnabled);
        File staged = new File(launch.getParentFile(), launch.getName() + ".part");
        try (FileOutputStream output = new FileOutputStream(staged)) {
            output.write(config.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            java.nio.file.Files.move(staged.toPath(), launch.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            staged.delete();
            throw error;
        }
    }

    static void writeWindowsLaunchConfig(File launch, File disk, File iso, File bootFloppy,
            boolean bootInstaller, int memoryMb, String cpuCore, boolean soundEnabled)
            throws IOException {
        writeConfigAtomically(launch, LaunchConfig.createWindowsInstall(disk.getAbsolutePath(),
                iso.getAbsolutePath(), bootFloppy.getAbsolutePath(), bootInstaller, memoryMb,
                cpuCore, soundEnabled));
    }

    private static void writeConfigAtomically(File launch, String config) throws IOException {
        File staged = new File(launch.getParentFile(), launch.getName() + ".part");
        try (FileOutputStream output = new FileOutputStream(staged)) {
            output.write(config.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            java.nio.file.Files.move(staged.toPath(), launch.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            staged.delete();
            throw error;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream input = new java.io.FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) output.write(buffer, 0, read);
            output.getFD().sync();
        }
    }

    private static void removeInterruptedImports(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) removeInterruptedImports(child);
        } else if (file.getName().endsWith(".part")) {
            file.delete();
        }
    }
}
