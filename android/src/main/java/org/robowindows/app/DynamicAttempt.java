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
    static final String PREPARED = DynamicAttemptState.PREPARED;
    static final String EXECUTING = DynamicAttemptState.EXECUTING;
    static final String RUNNING = DynamicAttemptState.RUNNING;
    static final String CLOSED_CLEAN = DynamicAttemptState.CLOSED_CLEAN;
    static final String NEEDS_CHECK = DynamicAttemptState.NEEDS_CHECK;
    static final String BLOCKED = DynamicAttemptState.BLOCKED;

    final String machineId;
    final String attemptId;
    final long generation;
    final String state;
    final JSONObject normalFallback;
    final int readinessMask;
    final String dynamicCyclePolicy;

    DynamicAttempt(String machineId, String attemptId, long generation, String state,
            JSONObject normalFallback) {
        this(machineId, attemptId, generation, state, normalFallback, 0,
                DynamicCyclePolicy.FIXED_20K.id);
    }

    DynamicAttempt(String machineId, String attemptId, long generation, String state,
            JSONObject normalFallback, int readinessMask) {
        this(machineId, attemptId, generation, state, normalFallback, readinessMask,
                DynamicCyclePolicy.FIXED_20K.id);
    }

    DynamicAttempt(String machineId, String attemptId, long generation, String state,
            JSONObject normalFallback, int readinessMask, String dynamicCyclePolicy) {
        this.machineId = machineId;
        this.attemptId = attemptId;
        this.generation = generation;
        this.state = state;
        this.normalFallback = normalFallback;
        this.readinessMask = readinessMask & DynamicReadiness.COMPLETE;
        this.dynamicCyclePolicy = dynamicCyclePolicy;
    }

    DynamicAttempt withState(String nextState) {
        if (!DynamicAttemptState.mayTransition(state, nextState)) {
            throw new IllegalArgumentException("Invalid dynamic attempt transition: " + state +
                    " -> " + nextState);
        }
        return new DynamicAttempt(machineId, attemptId, generation, nextState, normalFallback,
                readinessMask, dynamicCyclePolicy);
    }

    DynamicAttempt withReadiness(int evidence) {
        if (!RUNNING.equals(state)) {
            throw new IllegalStateException("Dynamic readiness requires a running attempt");
        }
        return new DynamicAttempt(machineId, attemptId, generation, state, normalFallback,
                DynamicReadiness.add(readinessMask, evidence), dynamicCyclePolicy);
    }

    /** Corrupt or mismatched durable records are conservatively terminal. */
    DynamicAttempt blocked() {
        return new DynamicAttempt(machineId, attemptId, generation, BLOCKED, normalFallback,
                readinessMask, dynamicCyclePolicy);
    }

    static DynamicAttempt read(File file) throws IOException {
        try {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8));
            return new DynamicAttempt(json.getString("machineId"), json.getString("attemptId"),
                    json.getLong("generation"), json.getString("state"),
                    json.getJSONObject("normalFallback"), json.optInt("readinessMask", 0),
                    readCyclePolicy(json));
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
            json.put("readinessMask", readinessMask);
            json.put("dynamicCyclePolicy", dynamicCyclePolicy);
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

    private static String readCyclePolicy(JSONObject json) {
        String policy = json.optString("dynamicCyclePolicy", "");
        if (!policy.isEmpty()) return policy;
        int legacyCycles = json.optInt("dynamicCycles",
                LaunchConfig.DYNAMIC_EXPERIMENTAL_CYCLES);
        try {
            return DynamicCyclePolicy.fromFixedCycles(legacyCycles).id;
        } catch (IllegalArgumentException ignored) {
            return "invalid";
        }
    }
}
