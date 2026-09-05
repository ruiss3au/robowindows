package org.robowindows.app;

import org.json.JSONException;
import org.json.JSONObject;

final class MediaAsset {
    final String name;
    final String sourcePath;
    final String runtimePath;
    final String sha256;
    final long importedAt;

    MediaAsset(String name, String sourcePath, String runtimePath, String sha256, long importedAt) {
        this.name = name;
        this.sourcePath = sourcePath;
        this.runtimePath = runtimePath;
        this.sha256 = sha256;
        this.importedAt = importedAt;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("sourcePath", sourcePath)
                .put("runtimePath", runtimePath)
                .put("sha256", sha256)
                .put("importedAt", importedAt);
    }

    static MediaAsset fromJson(JSONObject json) throws JSONException {
        return new MediaAsset(json.getString("name"), json.getString("sourcePath"),
                json.getString("runtimePath"), json.getString("sha256"),
                json.getLong("importedAt"));
    }
}
