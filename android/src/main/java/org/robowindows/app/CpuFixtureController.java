package org.robowindows.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;

/** Runs normal then dynamic fixtures and accepts only exact parity. */
final class CpuFixtureController {
    interface Listener {
        void onProgress(String message);
        void onComplete(boolean passed, String message);
    }

    private static final long STAGE_TIMEOUT_MILLIS = 10_000;
    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CpuFixtureClient client;
    private CpuFixtureResult legacyNormal;
    private final ExpandedCpuReport expanded = new ExpandedCpuReport();
    private int completedStages;
    private int generation;
    private boolean finished;

    CpuFixtureController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (client != null || finished) return;
        runStage("normal", null, NormalCpuFixtureService.class);
    }

    void cancel() {
        finished = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (client != null) client.disconnect();
        client = null;
    }

    private void runStage(String expectedMode, ExpandedCpuSuite expectedSuite,
            Class<? extends android.app.Service> serviceClass) {
        if (finished) return;
        String label = expectedSuite == null ? "v3 regressions" : expectedSuite.label;
        int total = 2 + ExpandedCpuSuite.values().length * 2;
        listener.onProgress("Testing " + expectedMode + " " + label + " (" +
                (completedStages + 1) + "/" + total + ")…");
        int stageGeneration = ++generation;
        int requestedSuiteId = expectedSuite == null ? CpuFixtureProtocol.LEGACY_SUITE_ID :
                expectedSuite.id;
        client = new CpuFixtureClient(context, serviceClass, requestedSuiteId,
                new CpuFixtureClient.Listener() {
            @Override public void onResult(String mode, int suiteId, byte[] record, String error) {
                if (finished || generation != stageGeneration) return;
                handler.removeCallbacksAndMessages(null);
                CpuFixtureClient completed = client;
                client = null;
                if (completed != null) completed.disconnect();
                if (error != null) {
                    fail(expectedMode + " core: " + error);
                    return;
                }
                if (!expectedMode.equals(mode)) {
                    fail("CPU fixture runner reported the wrong mode");
                    return;
                }
                if (suiteId != requestedSuiteId) {
                    fail("CPU fixture runner reported the wrong suite");
                    return;
                }
                try {
                    if (expectedSuite == null) {
                        CpuFixtureResult parsed = CpuFixtureResult.parse(record);
                        completedStages++;
                        if ("normal".equals(expectedMode)) {
                            legacyNormal = parsed;
                            runStage("dynamic", null, DynamicCpuFixtureService.class);
                        } else if (legacyNormal == null || !legacyNormal.matches(parsed)) {
                            fail("Normal and dynamic v3 CPU fixture results differ");
                        } else {
                            runStage("normal", ExpandedCpuSuite.values()[0],
                                    NormalCpuFixtureService.class);
                        }
                    } else {
                        ExpandedCpuResult parsed = ExpandedCpuResult.parse(record);
                        if (parsed.suite != expectedSuite) {
                            fail("Expanded CPU record reported the wrong suite");
                            return;
                        }
                        expanded.add("normal".equals(expectedMode) ?
                                ExpandedCpuReport.Mode.NORMAL : ExpandedCpuReport.Mode.DYNAMIC,
                                record);
                        completedStages++;
                        if ("normal".equals(expectedMode)) {
                            runStage("dynamic", expectedSuite, DynamicCpuFixtureService.class);
                        } else {
                            int next = expectedSuite.ordinal() + 1;
                            if (next < ExpandedCpuSuite.values().length) {
                                runStage("normal", ExpandedCpuSuite.values()[next],
                                        NormalCpuFixtureService.class);
                            } else {
                                expanded.requireComplete();
                                if (!CpuFixtureGate.markPassed(context, expanded)) {
                                    fail("CPU fixture result could not be saved");
                                } else {
                                    finished = true;
                                    listener.onComplete(true,
                                            "Complete x86 gate passed in normal and dynamic modes.");
                                }
                            }
                        }
                    }
                } catch (IOException failure) {
                    fail(expectedMode + " core: " + failure.getMessage());
                }
            }

            @Override public void onDisconnected() {
                if (!finished && generation == stageGeneration) {
                    fail(expectedMode + " CPU fixture process ended early");
                }
            }
        });
        handler.postDelayed(() -> {
            if (!finished && generation == stageGeneration) {
                fail(expectedMode + " CPU fixture timed out");
            }
        }, STAGE_TIMEOUT_MILLIS);
        try {
            client.start();
        } catch (IOException error) {
            fail(expectedMode + " core: " + error.getMessage());
        }
    }

    private void fail(String message) {
        if (finished) return;
        finished = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (client != null) client.disconnect();
        client = null;
        listener.onComplete(false, message);
    }
}
