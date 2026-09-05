package org.robowindows.app;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MachineProfile {
    static final int SCHEMA_VERSION = 5;

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
                json.optLong("lastBootedAt", 0), assets);
    }
}
