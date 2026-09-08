package org.robowindows.app;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MachineProfile {
    static final int SCHEMA_VERSION = 7;
    static final String ROLE_STABLE = "stable";
    static final String ROLE_EXPERIMENTAL = "experimental";
    static final String EXECUTION_NORMAL = "normal";
    static final String EXECUTION_DYNAMIC = "dynamic";

    final String id;
    final String name;
    final String family;
    final String mediaName;
    final String mediaPath;
    final String runtimePath;
    final String mediaSha256;
    final String launchPath;
    final int memoryMb;
    final String cpuCore;
    final boolean soundEnabled;
    final String role;
    final int fixedCycles;
    final int lastKnownSafeCycles;
    final String selectedExecution;
    final long configurationGeneration;
    final long createdAt;
    final long lastBootedAt;
    final List<MediaAsset> mediaAssets;

    MachineProfile(String id, String name, String family, String mediaName, String mediaPath,
            String runtimePath, String mediaSha256, String launchPath, int memoryMb, String cpuCore,
            boolean soundEnabled, long createdAt, long lastBootedAt) {
        this(id, name, family, mediaName, mediaPath, runtimePath, mediaSha256, launchPath,
                memoryMb, cpuCore, soundEnabled, createdAt, lastBootedAt,
                Collections.emptyList());
    }

    MachineProfile(String id, String name, String family, String mediaName, String mediaPath,
            String runtimePath, String mediaSha256, String launchPath, int memoryMb, String cpuCore,
            boolean soundEnabled, long createdAt, long lastBootedAt, List<MediaAsset> mediaAssets) {
        this(id, name, family, mediaName, mediaPath, runtimePath, mediaSha256, launchPath,
                memoryMb, cpuCore, soundEnabled, createdAt, lastBootedAt, mediaAssets,
                ROLE_STABLE, 0, 0);
    }

    MachineProfile(String id, String name, String family, String mediaName, String mediaPath,
            String runtimePath, String mediaSha256, String launchPath, int memoryMb, String cpuCore,
            boolean soundEnabled, long createdAt, long lastBootedAt, List<MediaAsset> mediaAssets,
            String role, int fixedCycles, int lastKnownSafeCycles) {
        this(id, name, family, mediaName, mediaPath, runtimePath, mediaSha256, launchPath,
                memoryMb, cpuCore, soundEnabled, createdAt, lastBootedAt, mediaAssets, role,
                fixedCycles, lastKnownSafeCycles, EXECUTION_NORMAL, 0);
    }

    MachineProfile(String id, String name, String family, String mediaName, String mediaPath,
            String runtimePath, String mediaSha256, String launchPath, int memoryMb, String cpuCore,
            boolean soundEnabled, long createdAt, long lastBootedAt, List<MediaAsset> mediaAssets,
            String role, int fixedCycles, int lastKnownSafeCycles, String selectedExecution,
            long configurationGeneration) {
        this.id = id;
        this.name = name;
        this.family = family;
        this.mediaName = mediaName;
        this.mediaPath = mediaPath;
        this.runtimePath = runtimePath;
        this.mediaSha256 = mediaSha256;
        this.launchPath = launchPath;
        this.memoryMb = memoryMb;
        this.cpuCore = cpuCore;
        this.soundEnabled = soundEnabled;
        this.role = ROLE_EXPERIMENTAL.equals(role) ? ROLE_EXPERIMENTAL : ROLE_STABLE;
        this.fixedCycles = fixedCycles;
        this.lastKnownSafeCycles = lastKnownSafeCycles;
        this.selectedExecution = EXECUTION_DYNAMIC.equals(selectedExecution)
                ? EXECUTION_DYNAMIC : EXECUTION_NORMAL;
        this.configurationGeneration = Math.max(0, configurationGeneration);
        this.createdAt = createdAt;
        this.lastBootedAt = lastBootedAt;
        this.mediaAssets = Collections.unmodifiableList(new ArrayList<>(mediaAssets));
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("schemaVersion", SCHEMA_VERSION);
        json.put("id", id);
        json.put("name", name);
        json.put("family", family);
        json.put("mediaName", mediaName);
        json.put("mediaPath", mediaPath);
        json.put("runtimePath", runtimePath);
        json.put("mediaSha256", mediaSha256);
        json.put("launchPath", launchPath);
        json.put("memoryMb", memoryMb);
        json.put("cpuCore", cpuCore);
        json.put("soundEnabled", soundEnabled);
        json.put("role", role);
        json.put("fixedCycles", fixedCycles);
        json.put("lastKnownSafeCycles", lastKnownSafeCycles);
        json.put("selectedExecution", selectedExecution);
        json.put("configurationGeneration", configurationGeneration);
        json.put("createdAt", createdAt);
        json.put("lastBootedAt", lastBootedAt);
        JSONArray assets = new JSONArray();
        for (MediaAsset asset : mediaAssets) assets.put(asset.toJson());
        json.put("mediaAssets", assets);
        return json;
    }

    static MachineProfile fromJson(JSONObject json) throws JSONException {
        int version = json.optInt("schemaVersion", 1);
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new JSONException("Unsupported machine schema");
        }
        String mediaPath = json.getString("mediaPath");
        String runtimePath = json.optString("runtimePath", mediaPath);
        String launchPath = json.optString("launchPath",
                new java.io.File(mediaPath).getParent() + "/launch.conf");
        ArrayList<MediaAsset> assets = new ArrayList<>();
        JSONArray encodedAssets = json.optJSONArray("mediaAssets");
        if (encodedAssets != null) {
            for (int i = 0; i < encodedAssets.length(); i++) {
                assets.add(MediaAsset.fromJson(encodedAssets.getJSONObject(i)));
            }
        }
        return new MachineProfile(json.getString("id"), json.getString("name"),
                json.getString("family"), json.getString("mediaName"),
                mediaPath, runtimePath, json.getString("mediaSha256"), launchPath,
                json.optInt("memoryMb", 64), json.optString("cpuCore", "normal"),
                json.optBoolean("soundEnabled", true), json.getLong("createdAt"),
                json.optLong("lastBootedAt", 0), assets,
                json.optString("role", ROLE_STABLE), json.optInt("fixedCycles", 0),
                json.optInt("lastKnownSafeCycles", 0),
                json.optString("selectedExecution", EXECUTION_NORMAL),
                json.optLong("configurationGeneration", 0));
    }

    boolean isExperimental() {
        return ROLE_EXPERIMENTAL.equals(role);
    }

    boolean isDynamicSelected() {
        return EXECUTION_DYNAMIC.equals(selectedExecution);
    }
}
