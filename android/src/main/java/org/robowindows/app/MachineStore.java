package org.robowindows.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private static final String CLEAN_SHUTDOWN_MACHINE = "clean_shutdown_machine";
    private static final String CLEAN_SHUTDOWN_MACHINES = "clean_shutdown_machines";
    private static final String DYNAMIC_ATTEMPT_FILE = "dynamic-attempt.json";
    private static final String SETTINGS_PENDING_FILE = "settings-pending";
    static final int DYNAMIC_EXPERIMENTAL_CYCLES = LaunchConfig.DYNAMIC_EXPERIMENTAL_CYCLES;
    private static final long COPY_SAFETY_MARGIN_BYTES = 256L * 1024L * 1024L;
    static final int SAFE_EXPERIMENTAL_CYCLES = 12000;
    static final int[] EXPERIMENTAL_CYCLE_CANDIDATES = {10000, 12000, 14000, 20000, 30000};
    private final Context context;
    private final File filesRoot;
    private final String preferencesName;
    private final FaultInjector faults;

    interface CopyProgress {
        void update(String stage, long completedBytes, long totalBytes);
    }

    interface FaultInjector {
        void before(String persistencePoint) throws IOException;
    }

    private static final FaultInjector NO_FAULTS = point -> {};

    MachineStore(Context context) {
        this(context, context.getFilesDir(), PREFS, NO_FAULTS);
    }

    MachineStore(Context context, File filesRoot, String preferencesName) {
        this(context, filesRoot, preferencesName, NO_FAULTS);
    }

    MachineStore(Context context, File filesRoot, String preferencesName, FaultInjector faults) {
        this.context = context.getApplicationContext();
        this.filesRoot = filesRoot;
        this.preferencesName = preferencesName;
        this.faults = faults == null ? NO_FAULTS : faults;
        removeInterruptedImports(new File(filesRoot, "machines"));
        new File(filesRoot, "system").mkdirs();
        new File(filesRoot, "saves").mkdirs();
    }

    /**
     * Records an experimental preference only. It deliberately does not write a
     * dynamic launch file or hand any media to native code.
     */
    synchronized MachineProfile selectDynamicProfile(MachineProfile selected) throws IOException {
        MachineProfile current = requireCurrent(selected);
        ensureSettingsReady(current);
        if (hasInterruptedSession() ||
                !hasCleanGuestShutdown(current.id)) {
            throw new IOException("DynRec requires a cleanly shut down machine");
        }
        validateWritableOwnership(current);
        if (requiresDynamicMediaCheck(current)) throw new IOException("Complete disk-check recovery first");
        MachineProfile updated = copyWithExecution(current, MachineProfile.EXECUTION_DYNAMIC,
                current.configurationGeneration + 1);
        replaceProfile(updated);
        return updated;
    }

    synchronized MachineProfile selectNormalProfile(MachineProfile selected) throws IOException {
        MachineProfile current = requireCurrent(selected);
        if (hasInterruptedSession()) throw new IOException("Stop the current machine session first");
        MachineProfile updated = copyWithExecution(current, MachineProfile.EXECUTION_NORMAL,
                current.configurationGeneration + 1);
        writeProfileLaunchConfig(updated, isWindowsInstaller(updated) && bootsInstaller(updated));
        try {
            replaceProfile(updated);
        } catch (IOException error) {
            writeProfileLaunchConfig(current, isWindowsInstaller(current) && bootsInstaller(current));
            throw error;
        }
        return updated;
    }

    /**
     * Performs every durable pre-native transition for a named diagnostic trial.
     * The caller may hand the returned launch file to a dedicated child process
     * only after this method returns successfully.
     */
    synchronized DynamicAttempt prepareDynamicStart(MachineProfile selected) throws IOException {
        return prepareDynamicStart(selected, DynamicCyclePolicy.FIXED_20K);
    }

    synchronized DynamicAttempt prepareDynamicStart(MachineProfile selected, int dynamicCycles)
            throws IOException {
        try {
            return prepareDynamicStart(selected, DynamicCyclePolicy.fromFixedCycles(dynamicCycles));
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    synchronized DynamicAttempt prepareDynamicStart(MachineProfile selected,
            DynamicCyclePolicy dynamicPolicy) throws IOException {
        if (dynamicPolicy == null) throw new IOException("Dynamic cycle policy is missing");
        MachineProfile current = requireCurrent(selected);
        ensureSettingsReady(current);
        if (!current.isDynamicSelected() || hasInterruptedSession() ||
                !hasCleanGuestShutdown(current.id)) {
            throw new IOException("Dynamic trial is not eligible to start");
        }
        validateWritableOwnership(current);
        File journal = dynamicAttemptFile(current);
        requireDynamicPolicy(current, dynamicPolicy);
        if (journal.exists()) throw new IOException("This experimental machine needs recovery first");
        DynamicAttempt prepared;
        try {
            prepared = new DynamicAttempt(current.id, UUID.randomUUID().toString(),
                    current.configurationGeneration, DynamicAttempt.PREPARED,
                    normalFallbackSettings(current), 0, dynamicPolicy.id);
        } catch (JSONException error) {
            throw new IOException("Cannot save dynamic fallback", error);
        }
        writeAttempt(prepared, journal, "journal-prepared");
        try {
            faults.before("dynamic-launch");
            writeDynamicLaunchConfig(current, dynamicPolicy);
            DynamicAttempt executing = prepared.withState(DynamicAttempt.EXECUTING);
            writeAttempt(executing, journal, "journal-executing");
            return executing;
        } catch (IOException | RuntimeException error) {
            // A launch publication failure has never handed media to native code.
            IOException rollbackFailure = null;
            try {
                writeProfileLaunchConfig(current,
                        isWindowsInstaller(current) && bootsInstaller(current));
            } catch (IOException restoreError) {
                rollbackFailure = restoreError;
            }
            if (!journal.delete()) {
                try {
                    prepared.withState(DynamicAttempt.BLOCKED).writeAtomically(journal);
                } catch (IOException markerError) {
                    if (rollbackFailure == null) rollbackFailure = markerError;
                }
                if (rollbackFailure == null) {
                    rollbackFailure = new IOException("Cannot clear failed dynamic trial record");
                }
            }
            if (rollbackFailure != null) {
                if (error instanceof IOException) rollbackFailure.addSuppressed(error);
                throw rollbackFailure;
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot prepare dynamic trial", error);
        }
    }

    /** Restores the normal launch description after any unfinished dynamic handoff. */
    synchronized boolean recoverDynamicAttempts() {
        boolean recovered = false;
        for (MachineProfile profile : load()) {
            File journal = dynamicAttemptFile(profile);
            if (!journal.exists()) continue;
            try {
                DynamicAttempt attempt = DynamicAttempt.read(journal);
                if (!profile.id.equals(attempt.machineId) ||
                        profile.configurationGeneration != attempt.generation) {
                    attempt.blocked().writeAtomically(journal);
                    continue;
                }
                MachineProfile fallback = normalFallbackProfile(profile, attempt.normalFallback);
                writeProfileLaunchConfig(fallback,
                        isWindowsInstaller(fallback) && bootsInstaller(fallback));
                if (DynamicAttempt.CLOSED_CLEAN.equals(attempt.state)) {
                    replaceProfile(copyWithExecution(fallback, MachineProfile.EXECUTION_DYNAMIC,
                            profile.configurationGeneration));
                    markCleanShutdownDurably(profile.id);
                } else {
                    replaceProfile(fallback);
                }
                if (DynamicAttempt.PREPARED.equals(attempt.state) ||
                        DynamicAttempt.CLOSED_CLEAN.equals(attempt.state)) {
                    if (!journal.delete()) throw new IOException("Cannot clear completed trial record");
                } else if (DynamicAttempt.EXECUTING.equals(attempt.state) ||
                        DynamicAttempt.RUNNING.equals(attempt.state)) {
                    attempt.withState(DynamicAttempt.NEEDS_CHECK).writeAtomically(journal);
                } else if (!DynamicAttempt.NEEDS_CHECK.equals(attempt.state) &&
                        !DynamicAttempt.BLOCKED.equals(attempt.state) &&
                        !DynamicAttempt.CLOSED_CLEAN.equals(attempt.state)) {
                    attempt.blocked().writeAtomically(journal);
                }
                recovered = true;
            } catch (IOException error) {
                // Keep an unreadable or failed record in place: this machine remains blocked.
            }
        }
        return recovered;
    }

    synchronized boolean requiresDynamicMediaCheck(MachineProfile selected) {
        return selected != null && dynamicAttemptFile(selected).exists();
    }

    /** Rechecks the exact durable handoff before the child may load native code. */
    synchronized MachineProfile validateDynamicChildHandoff(String machineId, String attemptId,
            long generation) throws IOException {
        return validateDynamicChildHandoff(machineId, attemptId, generation,
                DynamicCyclePolicy.FIXED_20K.id);
    }

    synchronized MachineProfile validateDynamicChildHandoff(String machineId, String attemptId,
            long generation, int dynamicCycles) throws IOException {
        try {
            return validateDynamicChildHandoff(machineId, attemptId, generation,
                    DynamicCyclePolicy.fromFixedCycles(dynamicCycles).id);
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    synchronized MachineProfile validateDynamicChildHandoff(String machineId, String attemptId,
            long generation, String dynamicPolicyId) throws IOException {
        DynamicCyclePolicy dynamicPolicy;
        try {
            dynamicPolicy = DynamicCyclePolicy.fromId(dynamicPolicyId);
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        }
        if (machineId == null || attemptId == null) {
            throw new IOException("Dynamic attempt identity is missing");
        }
        for (MachineProfile profile : load()) {
            if (!machineId.equals(profile.id)) continue;
            if (!profile.isDynamicSelected() ||
                    profile.configurationGeneration != generation) {
                throw new IOException("Dynamic attempt no longer matches this machine");
            }
            DynamicAttempt attempt = DynamicAttempt.read(dynamicAttemptFile(profile));
            if (!attemptId.equals(attempt.attemptId) || attempt.generation != generation ||
                    !machineId.equals(attempt.machineId) ||
                    !attempt.dynamicCyclePolicy.equals(dynamicPolicy.id) ||
                    (!DynamicAttempt.EXECUTING.equals(attempt.state) &&
                    !DynamicAttempt.RUNNING.equals(attempt.state))) {
                throw new IOException("Dynamic attempt is not authorized for native handoff");
            }
            requireDynamicPolicy(profile, dynamicPolicy);
            validateWritableOwnership(profile);
            return profile;
        }
        throw new IOException("Dynamic machine is unavailable");
    }

    synchronized DynamicAttempt markDynamicRunning(DynamicAttempt expected) throws IOException {
        DynamicAttempt current = requireCurrentDynamicAttempt(expected, DynamicAttempt.EXECUTING);
        DynamicAttempt running = current.withState(DynamicAttempt.RUNNING);
        running.writeAtomically(dynamicAttemptFile(requireProfile(expected.machineId, expected.generation)));
        return running;
    }

    synchronized DynamicAttempt markDynamicReadiness(DynamicAttempt expected, int evidence)
            throws IOException {
        DynamicAttempt current = requireCurrentDynamicAttempt(expected, DynamicAttempt.RUNNING);
        DynamicAttempt updated;
        try {
            updated = current.withReadiness(evidence);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new IOException(error.getMessage(), error);
        }
        updated.writeAtomically(dynamicAttemptFile(
                requireProfile(expected.machineId, expected.generation)));
        return updated;
    }

    /** Records verified guest shutdown only after native unload/flush has returned. */
    synchronized void closeDynamicAttemptCleanly(DynamicAttempt expected) throws IOException {
        DynamicAttempt current = requireCurrentDynamicAttempt(expected, DynamicAttempt.RUNNING);
        MachineProfile profile = requireProfile(expected.machineId, expected.generation);
        File journal = dynamicAttemptFile(profile);
        current.withState(DynamicAttempt.CLOSED_CLEAN).writeAtomically(journal);
        MachineProfile fallback = normalFallbackProfile(profile, current.normalFallback);
        writeProfileLaunchConfig(fallback, isWindowsInstaller(fallback) && bootsInstaller(fallback));
        faults.before("clean-preference");
        replaceProfile(copyWithExecution(fallback, MachineProfile.EXECUTION_DYNAMIC,
                profile.configurationGeneration));
        faults.before("clean-provenance");
        markCleanShutdownDurably(fallback.id);
        faults.before("clean-journal-clear");
        if (!journal.delete()) throw new IOException("Cannot clear clean dynamic trial record");
    }

    synchronized void quarantineDynamicAttempt(DynamicAttempt expected) throws IOException {
        DynamicAttempt current = requireCurrentDynamicAttempt(expected, null);
        if (DynamicAttempt.NEEDS_CHECK.equals(current.state)) return;
        if (!DynamicAttempt.EXECUTING.equals(current.state) &&
                !DynamicAttempt.RUNNING.equals(current.state)) {
            throw new IOException("Dynamic attempt cannot be quarantined from " + current.state);
        }
        current.withState(DynamicAttempt.NEEDS_CHECK).writeAtomically(
                dynamicAttemptFile(requireProfile(expected.machineId, expected.generation)));
    }

    /** Freshly validates an ordinary start; callers must not trust stale UI state. */
    synchronized MachineProfile prepareNormalStart(MachineProfile selected) throws IOException {
        MachineProfile current = requireCurrent(selected);
        ensureSettingsReady(current);
        if (current.isDynamicSelected()) {
            throw new IOException("Dynamic trials require the diagnostic runner");
        }
        if (hasInterruptedSession()) throw new IOException("Recover the previous session first");
        if (requiresDynamicMediaCheck(current)) {
            throw new IOException("This experimental disk needs a health check first");
        }
        validateWritableOwnership(current);
        return current;
    }

    private void markCleanShutdownDurably(String machineId) throws IOException {
        android.content.SharedPreferences p = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
        java.util.HashSet<String> clean = cleanShutdownMachines(p);
        clean.add(machineId);
        if (!p.edit().putStringSet(CLEAN_SHUTDOWN_MACHINES, clean)
                .remove(CLEAN_SHUTDOWN_MACHINE).commit()) {
            throw new IOException("Cannot record clean shutdown");
        }
    }

    /** Repair only derived configuration from the authoritative saved profile. */
    private void ensureSettingsReady(MachineProfile profile) throws IOException {
        File marker = new File(machineDirectory(profile), SETTINGS_PENDING_FILE);
        if (!marker.exists()) return;
        if (hasInterruptedSession() || requiresDynamicMediaCheck(profile)) {
            throw new IOException("Settings recovery requires a stopped, healthy machine");
        }
        validateWritableOwnership(profile);
        writeProfileLaunchConfig(profile, isWindowsInstaller(profile) && bootsInstaller(profile));
        if (!marker.delete()) throw new IOException("Cannot finish saved settings recovery");
    }

    synchronized String dynamicUnavailable(MachineProfile selected, boolean cpuPassed) {
        try {
            MachineProfile p = requireCurrent(selected);
            String reason = SettingsDraft.dynamicUnavailable(BuildConfig.DEBUG, p.isExperimental(),
                    hasInterruptedSession(), requiresDynamicMediaCheck(p),
                    hasCleanGuestShutdown(p.id), cpuPassed);
            if (reason != null) return reason;
            if (selectedUtility(p) != null) return "Use the Windows disk boot source before selecting DynRec.";
            validateWritableOwnership(p);
            return null;
        } catch (IOException error) {
            return "Machine storage or settings changed; reopen Settings or complete recovery.";
        }
    }

    synchronized MachineProfile saveSettings(MachineProfile selected, SettingsDraft draft,
            boolean cpuPassed) throws IOException {
        MachineProfile p = requireCurrent(selected);
        if (hasInterruptedSession() || requiresDynamicMediaCheck(p)) {
            throw new IOException("Stop the session and complete recovery before saving settings");
        }
        final String name;
        try { name = SettingsDraft.validatedName(draft.name); }
        catch (IllegalArgumentException error) { throw new IOException(error.getMessage()); }
        ensureSettingsReady(p);
        if (!PresentationPolicy.allowed(draft.presentationMode)) {
            throw new IOException("Unknown presentation mode");
        }
        if (draft.dynamic) {
            String reason = dynamicUnavailable(p, cpuPassed);
            if (reason != null) throw new IOException(reason);
        }
        if ((draft.memoryMb != p.memoryMb && draft.memoryMb != 16 && draft.memoryMb != 64) ||
                (!draft.normalCore.equals(p.cpuCore) && !draft.normalCore.equals("normal")) ||
                (p.isExperimental() ? !isExperimentalCycleCandidate(draft.normalCycles) :
                        draft.normalCycles != p.fixedCycles)) {
            throw new IOException("Unsupported settings selection");
        }
        validateWritableOwnership(p);
        if (!draft.dirty()) return p;
        MachineProfile updated = new MachineProfile(p.id, name, p.family, p.mediaName,
                p.mediaPath, p.runtimePath, p.mediaSha256, p.launchPath, draft.memoryMb,
                draft.normalCore, draft.sound, p.createdAt, p.lastBootedAt, p.mediaAssets,
                p.role, draft.normalCycles, p.lastKnownSafeCycles,
                draft.dynamic ? MachineProfile.EXECUTION_DYNAMIC : MachineProfile.EXECUTION_NORMAL,
                p.configurationGeneration + 1, draft.presentationMode);
        File marker = new File(machineDirectory(p), SETTINGS_PENDING_FILE);
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write(1); out.flush(); out.getFD().sync();
        }
        try {
            faults.before("settings-launch");
            writeProfileLaunchConfig(updated, isWindowsInstaller(updated) && bootsInstaller(updated));
            faults.before("settings-profile");
            replaceProfile(updated);
        } catch (IOException error) {
            try {
                faults.before("settings-rollback");
                replaceProfile(p);
                writeProfileLaunchConfig(p, isWindowsInstaller(p) && bootsInstaller(p));
                if (!marker.delete()) throw new IOException("Settings recovery remains pending");
            } catch (IOException rollback) { error.addSuppressed(rollback); }
            throw error;
        }
        if (!marker.delete()) throw new IOException("Settings saved; reopen Settings to finish recovery");
        return updated;
    }

    /** Explicit normal-core recovery boot for a user-approved quarantined copy. */
    synchronized MachineProfile prepareRecoveryStart(MachineProfile selected) throws IOException {
        MachineProfile current = requireCurrent(selected);
        if (hasInterruptedSession()) {
            throw new IOException("This machine is not eligible for recovery boot");
        }
        DynamicAttempt attempt = DynamicAttempt.read(dynamicAttemptFile(current));
        if (!DynamicAttempt.NEEDS_CHECK.equals(attempt.state) ||
                !current.id.equals(attempt.machineId) ||
                current.configurationGeneration != attempt.generation) {
            throw new IOException("This machine does not need recovery boot");
        }
        validateWritableOwnership(current);
        MachineProfile fallback = normalFallbackProfile(current, attempt.normalFallback);
        writeProfileLaunchConfig(fallback,
                isWindowsInstaller(fallback) && bootsInstaller(fallback));
        if (current.isDynamicSelected()) replaceProfile(fallback);
        return fallback;
    }

    /** Clears quarantine only after guest shutdown and normal native unload. */
    synchronized void closeRecoveryAfterCleanShutdown(MachineProfile selected) throws IOException {
        MachineProfile current = requireCurrent(selected);
        File journal = dynamicAttemptFile(current);
        DynamicAttempt attempt = DynamicAttempt.read(journal);
        if (!DynamicAttempt.NEEDS_CHECK.equals(attempt.state) ||
                !current.id.equals(attempt.machineId)) {
            throw new IOException("Cannot clear the recovered trial record");
        }
        android.content.SharedPreferences preferences = context.getSharedPreferences(preferencesName,
                Context.MODE_PRIVATE);
        java.util.HashSet<String> clean = cleanShutdownMachines(preferences);
        clean.add(current.id);
        if (!preferences.edit().putStringSet(CLEAN_SHUTDOWN_MACHINES, clean)
                .remove(CLEAN_SHUTDOWN_MACHINE).remove(ACTIVE_SESSION).commit()) {
            throw new IOException("Cannot record the recovery shutdown");
        }
        if (!journal.delete()) {
            throw new IOException("Cannot clear the recovered trial record");
        }
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
                                profile.lastBootedAt, profile.mediaAssets, profile.role,
                                profile.fixedCycles, profile.lastKnownSafeCycles,
                                profile.selectedExecution, profile.configurationGeneration, profile.presentationMode);
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
                        profile.mediaAssets, profile.role, profile.fixedCycles,
                        profile.lastKnownSafeCycles, profile.selectedExecution,
                        profile.configurationGeneration, profile.presentationMode));
                break;
            }
        }
        save(profiles);
    }

    void markSessionStarted(String machineId) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(preferencesName,
                Context.MODE_PRIVATE);
        java.util.HashSet<String> clean = cleanShutdownMachines(preferences);
        clean.remove(machineId);
        preferences.edit().putString(ACTIVE_SESSION, machineId)
                .putStringSet(CLEAN_SHUTDOWN_MACHINES, clean)
                .remove(CLEAN_SHUTDOWN_MACHINE).commit();
    }

    void markSessionStopped() {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
                .remove(ACTIVE_SESSION).commit();
    }

    boolean diagnosticsBlocked() {
        if (hasInterruptedSession()) return true;
        for (MachineProfile profile : load()) {
            if (requiresDynamicMediaCheck(profile)) return true;
        }
        return false;
    }

    boolean hasInterruptedSession() {
        return context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .contains(ACTIVE_SESSION);
    }

    void acknowledgeRecovery() {
        markSessionStopped();
    }

    void markGuestShutdown(String machineId) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(preferencesName,
                Context.MODE_PRIVATE);
        java.util.HashSet<String> clean = cleanShutdownMachines(preferences);
        clean.add(machineId);
        preferences.edit().putStringSet(CLEAN_SHUTDOWN_MACHINES, clean)
                .remove(CLEAN_SHUTDOWN_MACHINE).commit();
    }

    boolean hasCleanGuestShutdown(String machineId) {
        return cleanShutdownMachines(context.getSharedPreferences(preferencesName,
                Context.MODE_PRIVATE)).contains(machineId);
    }

    boolean recoverInterruptedExperimental() {
        String machineId = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString(ACTIVE_SESSION, null);
        if (machineId == null) return false;
        List<MachineProfile> profiles = load();
        for (int i = 0; i < profiles.size(); i++) {
            MachineProfile profile = profiles.get(i);
            if (!profile.id.equals(machineId) || !profile.isExperimental() ||
                    profile.fixedCycles == profile.lastKnownSafeCycles) continue;
            try {
                MachineProfile recovered = updatePerformanceProfile(profile,
                        profile.lastKnownSafeCycles);
                profiles.set(i, recovered);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    MachineProfile createExperimentalCopy(MachineProfile source) throws IOException {
        return createExperimentalCopy(source, null);
    }

    MachineProfile createExperimentalCopy(MachineProfile source, CopyProgress progress)
            throws IOException {
        if (source == null || !new File(source.runtimePath).isFile()) {
            throw new IOException("The source machine disk is unavailable");
        }
        if (hasInterruptedSession() || !hasCleanGuestShutdown(source.id)) {
            throw new IOException("Shut down the source machine inside Windows first");
        }
        long writableBytes = new File(source.runtimePath).length();
        for (MediaAsset asset : source.mediaAssets) {
            if (!asset.runtimePath.equals(asset.sourcePath)) {
                File runtime = new File(asset.runtimePath);
                if (!runtime.isFile()) throw new IOException("Attached writable media is unavailable");
                writableBytes += runtime.length();
            }
        }
        File directory = new File(filesRoot, "machines/" + UUID.randomUUID());
        File machinesDirectory = directory.getParentFile();
        if (machinesDirectory == null || machinesDirectory.getUsableSpace() <
                writableBytes + COPY_SAFETY_MARGIN_BYTES) {
            throw new IOException("Not enough storage for an independent copy");
        }
        if (!directory.mkdirs() || !directory.isDirectory()) {
            throw new IOException("Cannot create experimental machine storage");
        }
        CopyProgressReporter reporter = new CopyProgressReporter(progress, writableBytes * 3L);
        try {
            String extension = extension(new File(source.runtimePath).getName());
            if (!isBootMedia(extension)) throw new IOException("Unsupported source disk");
            File disk = new File(directory, "disk." + extension);
            copyAndVerify(new File(source.runtimePath), disk, reporter);
            ArrayList<MediaAsset> assets = copyWritableAssets(source.mediaAssets, directory, reporter);
            reporter.stage("Finalizing machine");
            File launch = new File(directory, "launch.conf");
            MachineProfile copy = new MachineProfile(UUID.randomUUID().toString(),
                    source.name + " - copy", source.family, source.mediaName, source.mediaPath,
                    disk.getAbsolutePath(), source.mediaSha256, launch.getAbsolutePath(),
                    source.memoryMb, "normal", source.soundEnabled, System.currentTimeMillis(), 0,
                    assets, MachineProfile.ROLE_EXPERIMENTAL, SAFE_EXPERIMENTAL_CYCLES,
                    SAFE_EXPERIMENTAL_CYCLES);
            writeProfileLaunchConfig(copy, isWindowsInstaller(source) && bootsInstaller(source));
            List<MachineProfile> profiles = load();
            profiles.add(copy);
            if (!save(profiles)) throw new IOException("Cannot save experimental machine");
            // The verified copy was made while its source was cleanly stopped.
            markGuestShutdown(copy.id);
            return copy;
        } catch (IOException error) {
            deleteTree(directory);
            throw error;
        }
    }

    MachineProfile updatePerformanceProfile(MachineProfile selected, int fixedCycles)
            throws IOException {
        if (!selected.isExperimental() || selected.isDynamicSelected() ||
                !isExperimentalCycleCandidate(fixedCycles)) {
            throw new IOException("This performance profile is unavailable");
        }
        MachineProfile updated = new MachineProfile(selected.id, selected.name, selected.family,
                selected.mediaName, selected.mediaPath, selected.runtimePath, selected.mediaSha256,
                selected.launchPath, selected.memoryMb, "normal", selected.soundEnabled,
                selected.createdAt, selected.lastBootedAt, selected.mediaAssets, selected.role,
                fixedCycles, selected.lastKnownSafeCycles, selected.selectedExecution,
                selected.configurationGeneration + 1, selected.presentationMode);
        boolean bootInstaller = isWindowsInstaller(selected) && bootsInstaller(selected);
        writeProfileLaunchConfig(updated, bootInstaller);
        try {
            replaceProfile(updated);
        } catch (IOException error) {
            writeProfileLaunchConfig(selected, bootInstaller);
            throw error;
        }
        return updated;
    }

    void deleteMachine(MachineProfile selected) throws IOException {
        if (selected == null || hasInterruptedSession()) {
            throw new IOException("A machine session must be stopped before deletion");
        }
        File machinesDirectory = new File(filesRoot, "machines").getCanonicalFile();
        File directory = new File(selected.runtimePath).getParentFile().getCanonicalFile();
        if (!directory.getParentFile().equals(machinesDirectory) || !directory.isDirectory()) {
            throw new IOException("The selected machine storage is unavailable");
        }
        List<MachineProfile> profiles = load();
        boolean removed = profiles.removeIf(profile -> profile.id.equals(selected.id));
        if (!removed) throw new IOException("The selected machine profile is unavailable");
        File staged = new File(machinesDirectory, ".delete-" + UUID.randomUUID());
        try {
            java.nio.file.Files.move(directory.toPath(), staged.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new IOException("The selected machine could not be staged for deletion", error);
        }
        if (!save(profiles)) {
            try {
                java.nio.file.Files.move(staged.toPath(), directory.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException restoreError) {
                throw new IOException("The machine profile could not be saved and its disk could " +
                        "not be restored", restoreError);
            }
            throw new IOException("The machine profile could not be saved");
        }
        android.content.SharedPreferences preferences = context.getSharedPreferences(preferencesName,
                Context.MODE_PRIVATE);
        java.util.HashSet<String> clean = cleanShutdownMachines(preferences);
        if (clean.remove(selected.id)) {
            preferences.edit().putStringSet(CLEAN_SHUTDOWN_MACHINES, clean)
                    .remove(CLEAN_SHUTDOWN_MACHINE).commit();
        }
        deleteTree(staged);
        if (staged.exists()) throw new IOException("The machine was removed but its staged files " +
                "need cleanup");
    }

    private static boolean isExperimentalCycleCandidate(int fixedCycles) {
        for (int candidate : EXPERIMENTAL_CYCLE_CANDIDATES) {
            if (candidate == fixedCycles) return true;
        }
        return false;
    }

    MachineProfile updateConfiguration(MachineProfile selected, int memoryMb, String cpuCore,
            boolean soundEnabled) throws IOException {
        if (selected.isDynamicSelected()) {
            throw new IOException("Normal settings cannot change during a dynamic trial");
        }
        String extension = extension(selected.mediaName);
        if (isWindowsInstaller(selected)) {
            writeWindowsLaunchConfig(new File(selected.launchPath), new File(selected.runtimePath),
                    new File(selected.mediaPath), windowsBootFloppy(selected),
                    bootsInstaller(selected), memoryMb, cpuCore, selected.fixedCycles, soundEnabled);
        } else {
            writeLaunchConfig(new File(selected.launchPath), new File(selected.runtimePath), extension,
                    memoryMb, cpuCore, selected.fixedCycles, soundEnabled);
        }
        MachineProfile updated = new MachineProfile(selected.id, selected.name, selected.family,
                selected.mediaName, selected.mediaPath, selected.runtimePath, selected.mediaSha256, selected.launchPath,
                memoryMb, cpuCore, soundEnabled, selected.createdAt, selected.lastBootedAt,
                selected.mediaAssets, selected.role, selected.fixedCycles,
                selected.lastKnownSafeCycles, selected.selectedExecution,
                selected.configurationGeneration + 1, selected.presentationMode);
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
                        selected.cpuCore, selected.fixedCycles, selected.soundEnabled);
            } else {
                writeLaunchConfig(new File(selected.launchPath), new File(selected.runtimePath),
                        extension, selected.memoryMb, selected.cpuCore, selected.fixedCycles,
                        selected.soundEnabled);
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

    File selectedUtility(MachineProfile profile) {
        if (!isWindowsInstaller(profile)) return null;
        File launch = new File(profile.launchPath);
        if (launch.length() > 65536) return null;
        try {
            String config = new String(java.nio.file.Files.readAllBytes(launch.toPath()), StandardCharsets.UTF_8);
            for (MediaAsset asset : profile.mediaAssets) {
                if (!LaunchConfig.supportsBoot(extension(asset.runtimePath))) continue;
                String escaped = asset.runtimePath.replace("\\", "\\\\").replace("\"", "\\\"");
                if (config.contains("boot \"" + escaped + "\"")) return new File(asset.runtimePath);
            }
        } catch (IOException ignored) { /* Start's ownership/config validation still applies. */ }
        return null;
    }

    void setWindowsInstallerBoot(MachineProfile profile, boolean installer) throws IOException {
        if (!isWindowsInstaller(profile)) throw new IOException("Not a Windows installer profile");
        writeWindowsLaunchConfig(new File(profile.launchPath), new File(profile.runtimePath),
                new File(profile.mediaPath), windowsBootFloppy(profile), installer,
                profile.memoryMb, profile.cpuCore, profile.fixedCycles, profile.soundEnabled);
    }

    void setWindowsUtilityBoot(MachineProfile profile, File utilityDisk) throws IOException {
        if (!isWindowsInstaller(profile)) throw new IOException("Not a Windows installer profile");
        String extension = extension(utilityDisk.getName());
        if (!LaunchConfig.supportsBoot(extension) || !utilityDisk.isFile()) {
            throw new IOException("Choose an IMG, IMA, or VHD utility disk");
        }
        writeConfigAtomically(new File(profile.launchPath), LaunchConfig.createWindowsUtility(
                profile.runtimePath, profile.mediaPath, utilityDisk.getAbsolutePath(),
                profile.memoryMb, profile.cpuCore, profile.fixedCycles, profile.soundEnabled));
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
                    profile.createdAt, profile.lastBootedAt, assets, profile.role,
                    profile.fixedCycles, profile.lastKnownSafeCycles,
                    profile.selectedExecution, profile.configurationGeneration + 1, profile.presentationMode));
            if (!save(profiles)) throw new IOException("Cannot save media metadata");
            return;
        }
        throw new IOException("Machine profile is unavailable");
    }

    private void writeProfileLaunchConfig(MachineProfile profile, boolean bootInstaller)
            throws IOException {
        File launch = new File(profile.launchPath);
        if (isWindowsInstaller(profile)) {
            File utility = selectedUtility(profile);
            if (utility != null) {
                writeConfigAtomically(launch, LaunchConfig.createWindowsUtility(profile.runtimePath,
                        profile.mediaPath, utility.getAbsolutePath(), profile.memoryMb,
                        profile.cpuCore, profile.fixedCycles, profile.soundEnabled));
                return;
            }
            writeWindowsLaunchConfig(launch, new File(profile.runtimePath), new File(profile.mediaPath),
                    windowsBootFloppy(profile), bootInstaller, profile.memoryMb, profile.cpuCore,
                    profile.fixedCycles, profile.soundEnabled);
        } else {
            writeLaunchConfig(launch, new File(profile.runtimePath),
                    extension(new File(profile.runtimePath).getName()), profile.memoryMb,
                    profile.cpuCore, profile.fixedCycles, profile.soundEnabled);
        }
    }

    private ArrayList<MediaAsset> copyWritableAssets(List<MediaAsset> sourceAssets, File directory,
            CopyProgressReporter reporter) throws IOException {
        ArrayList<MediaAsset> copied = new ArrayList<>();
        for (MediaAsset asset : sourceAssets) {
            if (asset.runtimePath.equals(asset.sourcePath)) {
                copied.add(asset);
                continue;
            }
            File source = new File(asset.runtimePath);
            String extension = extension(source.getName());
            File target = new File(directory, "media/" + UUID.randomUUID() + "-disk." + extension);
            File parent = target.getParentFile();
            if (!parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Cannot create copied media storage");
            }
            copyAndVerify(source, target, reporter);
            copied.add(new MediaAsset(asset.name, asset.sourcePath, target.getAbsolutePath(),
                    asset.sha256, asset.importedAt));
        }
        return copied;
    }

    private void replaceProfile(MachineProfile updated) throws IOException {
        List<MachineProfile> profiles = load();
        for (int i = 0; i < profiles.size(); i++) {
            if (!profiles.get(i).id.equals(updated.id)) continue;
            profiles.set(i, updated);
            if (save(profiles)) return;
            throw new IOException("Cannot save machine settings");
        }
        throw new IOException("Machine profile is unavailable");
    }

    private MachineProfile requireCurrent(MachineProfile selected) throws IOException {
        if (selected == null) throw new IOException("Machine profile is unavailable");
        for (MachineProfile current : load()) {
            if (!current.id.equals(selected.id)) continue;
            if (current.configurationGeneration != selected.configurationGeneration) {
                throw new IOException("Machine settings changed; reopen Settings");
            }
            return current;
        }
        throw new IOException("Machine profile is unavailable");
    }

    private MachineProfile requireProfile(String machineId, long generation) throws IOException {
        for (MachineProfile profile : load()) {
            if (profile.id.equals(machineId) && profile.configurationGeneration == generation) {
                return profile;
            }
        }
        throw new IOException("Dynamic machine settings changed");
    }

    private DynamicAttempt requireCurrentDynamicAttempt(DynamicAttempt expected, String state)
            throws IOException {
        MachineProfile profile = requireProfile(expected.machineId, expected.generation);
        DynamicAttempt current = DynamicAttempt.read(dynamicAttemptFile(profile));
        if (!expected.attemptId.equals(current.attemptId) ||
                expected.generation != current.generation ||
                (state != null && !state.equals(current.state))) {
            throw new IOException("Dynamic attempt is stale or no longer active");
        }
        return current;
    }

    private static MachineProfile copyWithExecution(MachineProfile profile, String execution,
            long generation) {
        return new MachineProfile(profile.id, profile.name, profile.family, profile.mediaName,
                profile.mediaPath, profile.runtimePath, profile.mediaSha256, profile.launchPath,
                profile.memoryMb, profile.cpuCore, profile.soundEnabled, profile.createdAt,
                profile.lastBootedAt, profile.mediaAssets, profile.role, profile.fixedCycles,
                profile.lastKnownSafeCycles, execution, generation, profile.presentationMode);
    }

    private void writeAttempt(DynamicAttempt attempt, File journal, String persistencePoint)
            throws IOException {
        faults.before(persistencePoint);
        attempt.writeAtomically(journal);
    }

    /**
     * The recovery marker deliberately carries no media metadata or paths. The
     * generation binds these execution settings to the authoritative profile,
     * which remains the only place that owns media bindings.
     */
    private static JSONObject normalFallbackSettings(MachineProfile profile) throws JSONException {
        JSONObject settings = new JSONObject();
        settings.put("execution", MachineProfile.EXECUTION_NORMAL);
        settings.put("memoryMb", profile.memoryMb);
        settings.put("cpuCore", profile.cpuCore);
        settings.put("soundEnabled", profile.soundEnabled);
        settings.put("fixedCycles", profile.fixedCycles);
        return settings;
    }

    private static MachineProfile normalFallbackProfile(MachineProfile profile, JSONObject settings)
            throws IOException {
        try {
            if (!MachineProfile.EXECUTION_NORMAL.equals(settings.getString("execution")) ||
                    settings.getInt("memoryMb") != profile.memoryMb ||
                    !profile.cpuCore.equals(settings.getString("cpuCore")) ||
                    settings.getBoolean("soundEnabled") != profile.soundEnabled ||
                    settings.getInt("fixedCycles") != profile.fixedCycles) {
                throw new IOException("Dynamic fallback does not match the machine record");
            }
        } catch (JSONException error) {
            throw new IOException("Dynamic fallback settings are invalid", error);
        }
        return copyWithExecution(profile, MachineProfile.EXECUTION_NORMAL,
                profile.configurationGeneration);
    }

    private File dynamicAttemptFile(MachineProfile profile) {
        return new File(machineDirectory(profile), DYNAMIC_ATTEMPT_FILE);
    }

    private File machineDirectory(MachineProfile profile) {
        return new File(profile.runtimePath).getParentFile();
    }

    private static void requireDynamicPolicy(MachineProfile profile, DynamicCyclePolicy policy)
            throws IOException {
        if (!policy.available(BuildConfig.DEBUG, profile.isExperimental())) {
            throw new IOException("This cycle policy requires a diagnostic experimental copy");
        }
    }

    private void validateWritableOwnership(MachineProfile profile) throws IOException {
        File root = new File(filesRoot, "machines").getCanonicalFile();
        File directory = machineDirectory(profile).getCanonicalFile();
        if (!directory.isDirectory() || !root.equals(directory.getParentFile())) {
            throw new IOException("Machine storage ownership is invalid");
        }
        validatePrivateWritableFile(profile.runtimePath, directory);
        for (MediaAsset asset : profile.mediaAssets) {
            if (!asset.runtimePath.equals(asset.sourcePath)) {
                validatePrivateWritableFile(asset.runtimePath, directory);
            }
        }
        for (MachineProfile other : load()) {
            if (other.id.equals(profile.id)) continue;
            rejectWritableAlias(profile.runtimePath, other.runtimePath);
            for (MediaAsset otherAsset : other.mediaAssets) {
                if (!otherAsset.runtimePath.equals(otherAsset.sourcePath)) {
                    rejectWritableAlias(profile.runtimePath, otherAsset.runtimePath);
                }
            }
            for (MediaAsset asset : profile.mediaAssets) {
                if (asset.runtimePath.equals(asset.sourcePath)) continue;
                rejectWritableAlias(asset.runtimePath, other.runtimePath);
                for (MediaAsset otherAsset : other.mediaAssets) {
                    if (!otherAsset.runtimePath.equals(otherAsset.sourcePath)) {
                        rejectWritableAlias(asset.runtimePath, otherAsset.runtimePath);
                    }
                }
            }
        }
    }

    private static void validatePrivateWritableFile(String path, File directory) throws IOException {
        File file = new File(path);
        File canonical = file.getCanonicalFile();
        if (!file.isFile() || !canonical.getPath().startsWith(directory.getPath() + File.separator)) {
            throw new IOException("Writable media is outside machine storage");
        }
    }

    private static void rejectWritableAlias(String first, String second) throws IOException {
        if (new File(first).getCanonicalFile().equals(new File(second).getCanonicalFile())) {
            throw new IOException("Writable media is shared with another machine");
        }
    }

    private void writeDynamicLaunchConfig(MachineProfile profile, DynamicCyclePolicy dynamicPolicy)
            throws IOException {
        String config;
        if (isWindowsInstaller(profile)) {
            config = LaunchConfig.createWindowsDynamic(profile.runtimePath, profile.mediaPath,
                    windowsBootFloppy(profile).getAbsolutePath(), bootsInstaller(profile),
                    profile.memoryMb, dynamicPolicy, profile.soundEnabled);
        } else {
            config = LaunchConfig.createDynamic(profile.runtimePath,
                    extension(new File(profile.runtimePath).getName()), profile.memoryMb,
                    dynamicPolicy, profile.soundEnabled);
        }
        writeConfigAtomically(new File(profile.launchPath), config);
    }

    private boolean save(List<MachineProfile> profiles) {
        JSONArray array = new JSONArray();
        for (MachineProfile profile : profiles) {
            try { array.put(profile.toJson()); } catch (JSONException ignored) {}
        }
        return context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
                .putString(KEY, array.toString()).commit();
    }

    private static java.util.HashSet<String> cleanShutdownMachines(
            android.content.SharedPreferences preferences) {
        java.util.HashSet<String> result = new java.util.HashSet<>();
        java.util.Set<String> recorded = preferences.getStringSet(CLEAN_SHUTDOWN_MACHINES, null);
        if (recorded != null) result.addAll(recorded);
        String legacy = preferences.getString(CLEAN_SHUTDOWN_MACHINE, null);
        if (legacy != null) result.add(legacy);
        return result;
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
        writeLaunchConfig(launch, media, extension, memoryMb, cpuCore, 0, soundEnabled);
    }

    static void writeLaunchConfig(File launch, File media, String extension, int memoryMb,
            String cpuCore, int fixedCycles, boolean soundEnabled) throws IOException {
        String config = LaunchConfig.create(media.getAbsolutePath(), extension, memoryMb,
                cpuCore, fixedCycles, soundEnabled);
        writeConfigAtomically(launch, config);
    }

    static void writeWindowsLaunchConfig(File launch, File disk, File iso, File bootFloppy,
            boolean bootInstaller, int memoryMb, String cpuCore, boolean soundEnabled)
            throws IOException {
        writeWindowsLaunchConfig(launch, disk, iso, bootFloppy, bootInstaller, memoryMb, cpuCore,
                0, soundEnabled);
    }

    static void writeWindowsLaunchConfig(File launch, File disk, File iso, File bootFloppy,
            boolean bootInstaller, int memoryMb, String cpuCore, int fixedCycles,
            boolean soundEnabled) throws IOException {
        writeConfigAtomically(launch, LaunchConfig.createWindowsInstall(disk.getAbsolutePath(),
                iso.getAbsolutePath(), bootFloppy.getAbsolutePath(), bootInstaller, memoryMb,
                cpuCore, fixedCycles, soundEnabled));
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

    private static void copyAndVerify(File source, File target, CopyProgressReporter reporter)
            throws IOException {
        File staged = new File(target.getParentFile(), target.getName() + ".part");
        try {
            String sourceHash = sha256(source, reporter, "Verifying source disk");
            copyFileTracked(source, staged, reporter, "Copying disk");
            String stagedHash = sha256(staged, reporter, "Verifying copied disk");
            if (!sourceHash.equals(stagedHash)) throw new IOException("Copied disk checksum mismatch");
            java.nio.file.Files.move(staged.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            staged.delete();
            throw error;
        }
    }

    private static String sha256(File file, CopyProgressReporter reporter, String stage)
            throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            reporter.stage(stage);
            try (InputStream input = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[128 * 1024];
                for (int read; (read = input.read(buffer)) != -1;) {
                    digest.update(buffer, 0, read);
                    reporter.advance(read);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static void copyFileTracked(File source, File target, CopyProgressReporter reporter,
            String stage) throws IOException {
        reporter.stage(stage);
        try (InputStream input = new java.io.FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) {
                output.write(buffer, 0, read);
                reporter.advance(read);
            }
            output.getFD().sync();
        }
    }

    private static final class CopyProgressReporter {
        private final CopyProgress callback;
        private final long totalBytes;
        private long completedBytes;
        private long reportedBytes;

        CopyProgressReporter(CopyProgress callback, long totalBytes) {
            this.callback = callback;
            this.totalBytes = Math.max(1L, totalBytes);
            stage("Preparing copy");
        }

        void stage(String stage) {
            if (callback != null) callback.update(stage, completedBytes, totalBytes);
        }

        void advance(long bytes) {
            completedBytes = Math.min(totalBytes, completedBytes + bytes);
            if (callback != null && (completedBytes == totalBytes ||
                    completedBytes - reportedBytes >= 1024L * 1024L)) {
                reportedBytes = completedBytes;
                callback.update("", completedBytes, totalBytes);
            }
        }
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
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
