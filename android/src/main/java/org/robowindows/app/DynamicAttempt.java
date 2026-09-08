package org.robowindows.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Durable, per-machine record of a dynamic-core handoff. */
final class DynamicAttempt {
    static final String PREPARED = "prepared";
    static final String EXECUTING = "executing";
    static final String RUNNING = "running";
    static final String NEEDS_CHECK = "needs-check";
    static final String BLOCKED = "blocked";

    final String machineId;
    final String attemptId;
    final long generation;
    final String state;
    final JSONObject normalFallback;

    DynamicAttempt(String machineId, String attemptId, long generation, String state,
            JSONObject normalFallback) {
        this.machineId = machineId;
        this.attemptId = attemptId;
        this.generation = generation;
        this.state = state;
        this.normalFallback = normalFallback;
    }

    DynamicAttempt withState(String nextState) {
        return new DynamicAttempt(machineId, attemptId, generation, nextState, normalFallback);
    }

    static DynamicAttempt read(File file) throws IOException {
        try {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8));
            return new DynamicAttempt(json.getString("machineId"), json.getString("attemptId"),
                    json.getLong("generation"), json.getString("state"),
                    json.getJSONObject("normalFallback"));
        } catch (JSONException error) {
            throw new IOException("Dynamic recovery record is invalid", error);
        }
    }

    void writeAtomically(File file) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("machineId", machineId);
            json.put("attemptId", attemptId);
            json.put("generation", generation);
            json.put("state", state);
            json.put("normalFallback", normalFallback);
        } catch (JSONException error) {
            throw new IOException("Cannot encode dynamic recovery record", error);
        }
        File staged = new File(file.getParentFile(), file.getName() + ".part");
        try (FileOutputStream output = new FileOutputStream(staged)) {
            output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            Files.move(staged.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            staged.delete();
            throw error;
        }
    }
}
