package org.robowindows.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.hardware.input.InputManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.format.DateUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;

public final class MainActivity extends Activity {
    private interface DynamicCommand {
        void run(DynamicTrialController controller) throws IOException;
    }

    private static final int BG = Color.rgb(16, 19, 24);
    private static final int SURFACE = Color.rgb(25, 30, 38);
    private static final int SURFACE_HIGH = Color.rgb(34, 42, 53);
    private static final int PRIMARY = Color.rgb(117, 213, 181);
    private static final int TEXT = Color.rgb(238, 243, 247);
    private static final int MUTED = Color.rgb(157, 170, 183);
    private static final int PICK_MEDIA = 41;
    private static final int CHANGE_MEDIA = 42;
    private static final int WINDOWS_UTILITY = 43;
    private static final String DYNAMIC_DIAGNOSTIC = "robowindows.runDynamicDiagnostic";
    private static final String DYNAMIC_DIAGNOSTIC_CYCLES =
            "robowindows.dynamicDiagnosticCycles";
    private static final String DYNAMIC_DIAGNOSTIC_POLICY =
            "robowindows.dynamicDiagnosticPolicy";

    private LinearLayout content;
    private boolean pointerCaptured;
    private final SessionUiState sessionUiState = new SessionUiState();
    private GuestDisplayView sessionGuest;
    private View sessionControls;
    private boolean consumingRevealTouch;
    private MachineStore machineStore;
    private boolean experimentalRecoveryApplied;
    private boolean dynamicRecoveryApplied;
    private boolean copyInProgress;
    private ProgressBar copyProgressBar;
    private TextView copyProgressStatus;
    private TextView copyProgressPercent;
    private String pendingFamily;
    private boolean sessionActive;
    private boolean sessionPaused;
    private long sessionGeneration;
    private MachineProfile currentSessionProfile;
    private DynamicTrialController dynamicTrial;
    private CpuFixtureController cpuFixtureController;
    private boolean recoverySession;
    private boolean recoveryGuestShutdownObserved;
    private MachineProfile pendingUtilityProfile;
    private boolean transientDebugSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean appPaused;
    private boolean audioFocusPaused;
    private TextView diagnosticStats;
    private LinearLayout diagnosticDevices;
    private final HashMap<Integer, Integer> deviceHandles = new HashMap<>();
    private int nextDeviceHandle = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideSessionControls = () -> {
        sessionUiState.update(android.os.SystemClock.uptimeMillis());
        syncSessionControls();
    };
    private final Runnable captureRequestTimeout = () -> {
        if (!sessionUiState.captureRequestTimedOut(android.os.SystemClock.uptimeMillis())) return;
        syncSessionControls();
        Toast.makeText(this, "Mouse capture is unavailable. Controls remain available.",
                Toast.LENGTH_LONG).show();
    };
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener = focusChange -> {
        if (!sessionActive) return;
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            audioFocusPaused = false;
            if (!appPaused && !sessionPaused) {
                sessionUiState.resume(android.os.SystemClock.uptimeMillis());
                syncSessionControls();
                scheduleControlsHide();
                setActiveSessionPaused(false);
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            audioFocusPaused = true;
            releasePointerCaptureAndCancel();
            sessionUiState.pause(android.os.SystemClock.uptimeMillis());
            syncSessionControls();
            setActiveSessionPaused(true);
        }
    };
    private final Runnable refreshInputStats = new Runnable() {
        @Override public void run() {
            if (diagnosticStats == null) return;
            diagnosticStats.setText(NativeHost.inputStats());
            handler.postDelayed(this, 250);
        }
    };
    private final InputManager.InputDeviceListener inputListener =
            new InputManager.InputDeviceListener() {
                @Override public void onInputDeviceAdded(int deviceId) { refreshDevices(); }
                @Override public void onInputDeviceChanged(int deviceId) { refreshDevices(); }
                @Override public void onInputDeviceRemoved(int deviceId) {
                    deviceHandles.remove(deviceId);
                    releasePointerAndShowControls();
                    refreshDevices();
                }
            };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        machineStore = new MachineStore(this);
        experimentalRecoveryApplied = machineStore.recoverInterruptedExperimental();
        dynamicRecoveryApplied = machineStore.recoverDynamicAttempts();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        ((InputManager) getSystemService(Context.INPUT_SERVICE))
                .registerInputDeviceListener(inputListener, handler);
        showHome();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            String windowsUtility = getIntent().getStringExtra("robowindows.testWindowsUtility");
            if (windowsUtility != null) {
                try {
                    MachineProfile profile = null;
                    for (MachineProfile candidate : machineStore.load()) {
                        if (machineStore.isWindowsInstaller(candidate)) profile = candidate;
                    }
                    if (profile == null) throw new IOException("No Windows installer profile");
                    java.io.File media = machineStore.importAdditionalMedia(
                            Uri.fromFile(new java.io.File(windowsUtility)), profile.id);
                    machineStore.setWindowsUtilityBoot(profile, media);
                    showSession(profile);
                    handler.postDelayed(() -> NativeHost.setPaused(false), 500);
                } catch (IOException error) {
                    android.util.Log.e("RoboWindowsTest", "Windows utility import failed", error);
                }
                return;
            }
            String windowsIso = getIntent().getStringExtra("robowindows.testWindowsIso");
            if (windowsIso != null && windowsIso.endsWith(".iso")) {
                try {
                    MachineProfile profile = machineStore.importMachine(
                            Uri.fromFile(new java.io.File(windowsIso)), "Windows");
                    showSession(profile);
                    // Device regression must progress even if the secure keyguard covers it.
                    handler.postDelayed(() -> NativeHost.setPaused(false), 500);
                } catch (IOException error) {
                    android.util.Log.e("RoboWindowsTest", "Windows installer import failed", error);
                }
                return;
            }
            if (getIntent().getBooleanExtra("robowindows.testPersistence", false)) {
                DebugSelfTest.run(this);
            }
            if (getIntent().getBooleanExtra("robowindows.runCpuDiagnostic", false)) {
                getIntent().removeExtra("robowindows.runCpuDiagnostic");
                runCpuDiagnostic();
                return;
            }
            if (getIntent().getBooleanExtra(DYNAMIC_DIAGNOSTIC, false)) {
                String dynamicPolicy = requestedDynamicPolicy(getIntent());
                getIntent().removeExtra(DYNAMIC_DIAGNOSTIC);
                getIntent().removeExtra(DYNAMIC_DIAGNOSTIC_CYCLES);
                getIntent().removeExtra(DYNAMIC_DIAGNOSTIC_POLICY);
                runDynamicDiagnostic(dynamicPolicy);
                return;
            }
            if (getIntent().getBooleanExtra("robowindows.runRecoveryBoot", false)) {
                runRecoveryBoot();
                return;
            }
            String testConfig = getIntent().getStringExtra("robowindows.testConfig");
            if (testConfig != null && testConfig.endsWith(".conf")) {
                transientDebugSession = true;
                long now = System.currentTimeMillis();
                MachineProfile testProfile = new MachineProfile("debug-test", "Display test", "DOS",
                        "test.img", testConfig, testConfig, "", testConfig, 16, "normal",
                        false, now, 0);
                showSession(testProfile);
                // ADB framebuffer validation must run even when System UI covers the debug activity.
                handler.postDelayed(() -> NativeHost.setPaused(false), 500);
                if (getIntent().getBooleanExtra("robowindows.testLifecycle", false)) {
                    handler.postDelayed(() -> NativeHost.setPaused(true), 1000);
                    handler.postDelayed(() -> NativeHost.setPaused(false), 1500);
                    String testMedia = getIntent().getStringExtra("robowindows.testMedia");
                    if (testMedia != null) {
                        handler.postDelayed(() -> NativeHost.changeMedia(testMedia), 2000);
                    }
                    handler.postDelayed(() -> restartSession(testProfile), 3500);
                    handler.postDelayed(this::stopActiveSession, 5000);
                }
            }
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        if (intent.getBooleanExtra("robowindows.runCpuDiagnostic", false)) {
            intent.removeExtra("robowindows.runCpuDiagnostic");
            handler.post(() -> {
                if (sessionActive) {
                    Toast.makeText(this, "Stop the current session first.",
                            Toast.LENGTH_LONG).show();
                } else {
                    runCpuDiagnostic();
                }
            });
            return;
        }
        if (intent.getBooleanExtra(DYNAMIC_DIAGNOSTIC, false)) {
            String dynamicPolicy = requestedDynamicPolicy(intent);
            intent.removeExtra(DYNAMIC_DIAGNOSTIC);
            intent.removeExtra(DYNAMIC_DIAGNOSTIC_CYCLES);
            intent.removeExtra(DYNAMIC_DIAGNOSTIC_POLICY);
            handler.post(() -> {
                if (sessionActive) {
                    Toast.makeText(this, "Stop the current session first.",
                            Toast.LENGTH_LONG).show();
                } else {
                    runDynamicDiagnostic(dynamicPolicy);
                }
            });
            return;
        }
        if (intent.getBooleanExtra("robowindows.runRecoveryBoot", false)) {
            if (sessionActive) {
                Toast.makeText(this, "Stop the current session first.", Toast.LENGTH_LONG).show();
            } else {
                runRecoveryBoot();
            }
            return;
        }
        int keyCode = intent.getIntExtra("robowindows.testKey", -1);
        if (keyCode < 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        pushSessionKey(KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0,
                InputDevice.SOURCE_KEYBOARD, 0, now * 1_000_000L);
        // Keep the key down across several guest input polls. An immediate down/up pair can be
        // entirely missed by a libretro core between frames.
        handler.postDelayed(() -> {
            long releasedAt = android.os.SystemClock.uptimeMillis();
            pushSessionKey(KeyEvent.ACTION_UP, keyCode, 0, 0, 0,
                    InputDevice.SOURCE_KEYBOARD, 0, releasedAt * 1_000_000L);
        }, 75);
    }

    /** Deliberately debug-only: ADB must explicitly request the one guarded diagnostic. */
    private static String requestedDynamicPolicy(Intent intent) {
        String policyId = intent.getStringExtra(DYNAMIC_DIAGNOSTIC_POLICY);
        if (policyId != null) return policyId;
        int cycles = intent.getIntExtra(DYNAMIC_DIAGNOSTIC_CYCLES,
                LaunchConfig.DYNAMIC_EXPERIMENTAL_CYCLES);
        try {
            return DynamicCyclePolicy.fromFixedCycles(cycles).id;
        } catch (IllegalArgumentException ignored) {
            return "invalid";
        }
    }

    private void runDynamicDiagnostic(String dynamicPolicyId) {
        DynamicCyclePolicy dynamicPolicy;
        try {
            dynamicPolicy = DynamicCyclePolicy.fromId(dynamicPolicyId);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, "Unsupported dynamic diagnostic cycle value.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!CpuFixtureGate.passed(this)) {
            Toast.makeText(this, "Run and pass the CPU test for this build first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        MachineProfile experimental = null;
        for (MachineProfile candidate : machineStore.load()) {
            if (!candidate.isExperimental()) continue;
            if (experimental != null) {
                Toast.makeText(this, "Dynamic diagnostic needs exactly one experimental copy.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            experimental = candidate;
        }
        if (experimental == null) {
            Toast.makeText(this, "No experimental copy is available for the diagnostic.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            showDynamicDiagnostic(machineStore.selectDynamicProfile(experimental), dynamicPolicy);
        } catch (IOException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runCpuDiagnostic() {
        if (cpuFixtureController != null) return;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(28), dp(22), dp(28), dp(22));
        page.setBackgroundColor(BG);
        Button back = button("Back", v -> {
            cancelCpuDiagnostic();
            showHome();
        });
        page.addView(back, new LinearLayout.LayoutParams(dp(110), dp(46)));
        TextView title = text("CPU correctness test", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(24);
        page.addView(title, titleParams);
        TextView explanation = text("Runs a RoboWindows test image in isolated normal and " +
                "dynamic processes. No machine disk is opened.", 16, MUTED);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        explanationParams.topMargin = dp(12);
        page.addView(explanation, explanationParams);
        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        progressParams.topMargin = dp(28);
        page.addView(progress, progressParams);
        TextView status = text("Preparing CPU fixture…", 17, TEXT);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        page.addView(status, statusParams);
        setContentView(page);

        CpuFixtureController controller = new CpuFixtureController(this,
                new CpuFixtureController.Listener() {
                    @Override public void onProgress(String message) {
                        if (cpuFixtureController != null) status.setText(message);
                    }

                    @Override public void onComplete(boolean passed, String message) {
                        if (cpuFixtureController == null) return;
                        cpuFixtureController = null;
                        progress.setVisibility(View.GONE);
                        status.setText(message);
                        back.setText("Done");
                        android.util.Log.i("RoboWindowsCpuFixture",
                                "status=" + (passed ? "pass" : "fail") + " " + message);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
        cpuFixtureController = controller;
        controller.start();
    }

    private void cancelCpuDiagnostic() {
        CpuFixtureController controller = cpuFixtureController;
        cpuFixtureController = null;
        if (controller != null) controller.cancel();
    }

    private void runRecoveryBoot() {
        for (MachineProfile candidate : machineStore.load()) {
            if (candidate.isExperimental() && machineStore.requiresDynamicMediaCheck(candidate)) {
                showSession(candidate, true);
                return;
            }
        }
        Toast.makeText(this, "No quarantined experimental copy is available.", Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(BG);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(background(PRIMARY, 12));
        button.setOnClickListener(listener);
        button.setPadding(dp(18), 0, dp(18), 0);
        return button;
    }

    // Product-wide selected-control treatment: retain the dark interface surface and use the
    // primary color only for the active label and checkmark, rather than a separate status line.
    private Button selectedButton(String label, View.OnClickListener listener) {
        Button button = button("✓ " + label, listener);
        button.setTextColor(PRIMARY);
        button.setBackground(background(SURFACE_HIGH, 12));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(background(SURFACE, 16));
        return card;
    }

    private void showHome() {
        diagnosticStats = null;
        diagnosticDevices = null;
        handler.removeCallbacks(refreshInputStats);
        stopActiveSession();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(28), dp(22), dp(28), dp(22));
        root.setBackgroundColor(BG);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(0, 0, dp(28), 0);
        TextView brand = text("ROBOWINDOWS", 15, PRIMARY);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rail.addView(brand);
        TextView buildIdentity = text(BuildIdentity.label(
                BuildConfig.VERSION_NAME, BuildConfig.SOURCE_REVISION), 12, MUTED);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        identityParams.topMargin = dp(3);
        rail.addView(buildIdentity, identityParams);
        LinearLayout.LayoutParams diagnosticParams = new LinearLayout.LayoutParams(dp(190), dp(46));
        diagnosticParams.topMargin = dp(22);
        rail.addView(button("Input test", v -> showDiagnostics()), diagnosticParams);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            LinearLayout.LayoutParams cpuParams = new LinearLayout.LayoutParams(dp(190), dp(46));
            cpuParams.topMargin = dp(12);
            rail.addView(button(CpuFixtureGate.passed(this) ? "✓ CPU test" : "CPU test",
                    v -> runCpuDiagnostic()), cpuParams);
        }

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("Machines", 28, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(button("Add machine", v -> showAddMachine()),
                new LinearLayout.LayoutParams(dp(180), dp(48)));
        content.addView(header);

        if (machineStore.hasInterruptedSession()) {
            LinearLayout recovery = card();
            TextView message = text("The previous session did not close normally. Your imported media is safe.",
                    15, TEXT);
            recovery.addView(message);
            LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(dp(130), dp(44));
            dismissParams.topMargin = dp(12);
            recovery.addView(button("Dismiss", v -> {
                machineStore.acknowledgeRecovery();
                showHome();
            }), dismissParams);
            LinearLayout.LayoutParams recoveryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            recoveryParams.topMargin = dp(16);
            content.addView(recovery, recoveryParams);
        }
        if (experimentalRecoveryApplied) {
            LinearLayout recovery = card();
            recovery.addView(text("An interrupted experimental run was restored to its safe " +
                    "performance profile.", 15, TEXT));
            LinearLayout.LayoutParams recoveryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            recoveryParams.topMargin = dp(16);
            content.addView(recovery, recoveryParams);
        }
        if (dynamicRecoveryApplied) {
            LinearLayout recovery = card();
            recovery.addView(text("An unfinished dynamic trial was returned to its normal " +
                    "profile. Its experimental disk remains quarantined for a health check.",
                    15, TEXT));
            LinearLayout.LayoutParams recoveryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            recoveryParams.topMargin = dp(16);
            content.addView(recovery, recoveryParams);
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout library = new LinearLayout(this);
        library.setOrientation(LinearLayout.VERTICAL);
        List<MachineProfile> profiles = machineStore.load();
        if (profiles.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER);
            TextView title = text("No machines yet", 22, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            empty.addView(title);
            TextView copy = text("Add a DOS or Windows machine to get started.", 14, MUTED);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            copyParams.topMargin = dp(8);
            empty.addView(copy, copyParams);
            library.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        } else {
            for (MachineProfile profile : profiles) addMachineCard(library, profile);
        }
        scroll.addView(library);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(20);
        content.addView(scroll, scrollParams);

        root.addView(rail, new LinearLayout.LayoutParams(dp(278), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        setContentView(root);
    }

    private void addMachineCard(LinearLayout library, MachineProfile profile) {
        LinearLayout item = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(profile.name, 20, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(name);
        String detail = profile.isExperimental() ? "Experimental copy · " + profile.family :
                profile.family;
        if (profile.lastBootedAt > 0) {
            detail += " · Used " + DateUtils.getRelativeTimeSpanString(profile.lastBootedAt,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        }
        TextView family = text(detail, 14, MUTED);
        LinearLayout.LayoutParams familyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        familyParams.topMargin = dp(4);
        labels.addView(family, familyParams);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button("Settings", v -> showSettings(profile)),
                new LinearLayout.LayoutParams(dp(140), dp(48)));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(130), dp(48));
        startParams.leftMargin = dp(10);
        String startLabel = machineStore.isWindowsInstaller(profile) &&
                machineStore.bootsInstaller(profile) ? "Install" : "Start";
        Button start = button(startLabel, v -> showSession(profile));
        if (machineStore.requiresDynamicMediaCheck(profile)) {
            start.setText("Needs disk check");
            start.setOnClickListener(v -> showSession(profile, true));
        }
        row.addView(start, startParams);
        item.addView(row);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.bottomMargin = dp(12);
        library.addView(item, itemParams);
    }

    private void showSettings(MachineProfile profile) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(28), dp(22), dp(28), dp(22));
        page.setBackgroundColor(BG);
        page.addView(button("Back", v -> showHome()), new LinearLayout.LayoutParams(dp(110), dp(46)));
        TextView title = text(profile.name, 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(24);
        page.addView(title, titleParams);
        TextView summary = text(configurationSummary(profile), 16, MUTED);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(10);
        page.addView(summary, summaryParams);
        LinearLayout presets = new LinearLayout(this);
        LinearLayout.LayoutParams preset = new LinearLayout.LayoutParams(dp(230), dp(54));
        presets.addView(button("DOS compatibility", v -> saveConfiguration(profile, 16, "normal",
                profile.soundEnabled)), preset);
        LinearLayout.LayoutParams balanced = new LinearLayout.LayoutParams(dp(230), dp(54));
        balanced.leftMargin = dp(12);
        presets.addView(button("Windows compatible", v -> saveConfiguration(profile, 64, "normal",
                profile.soundEnabled)), balanced);
        LinearLayout.LayoutParams presetsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetsParams.topMargin = dp(24);
        page.addView(presets, presetsParams);
        if (profile.isExperimental()) {
            TextView performance = text("Performance trial: normal CPU only. Each option uses " +
                    "a separate disk from the stable machine.", 15, MUTED);
            LinearLayout.LayoutParams performanceParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            performanceParams.topMargin = dp(20);
            page.addView(performance, performanceParams);
            LinearLayout trials = new LinearLayout(this);
            trials.setOrientation(LinearLayout.VERTICAL);
            LinearLayout trialRow = null;
            int[] cycles = MachineStore.EXPERIMENTAL_CYCLE_CANDIDATES;
            for (int index = 0; index < cycles.length; index++) {
                int cycle = cycles[index];
                if (index % 2 == 0) {
                    trialRow = new LinearLayout(this);
                    if (index > 0) {
                        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        rowParams.topMargin = dp(12);
                        trials.addView(trialRow, rowParams);
                    } else {
                        trials.addView(trialRow);
                    }
                }
                LinearLayout.LayoutParams trialParams = new LinearLayout.LayoutParams(dp(130), dp(54));
                if (trialRow.getChildCount() > 0) trialParams.leftMargin = dp(12);
                String label = cycle / 1000 + "k cycles";
                Button trial = profile.fixedCycles == cycle ? selectedButton(label, v ->
                        savePerformanceProfile(profile, cycle)) : button(label, v ->
                        savePerformanceProfile(profile, cycle));
                trialRow.addView(trial, trialParams);
            }
            LinearLayout.LayoutParams trialsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trialsParams.topMargin = dp(12);
            page.addView(trials, trialsParams);
        } else {
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(270), dp(54));
            copyParams.topMargin = dp(20);
            page.addView(button("Create experimental copy", v -> createExperimentalCopy(profile)),
                    copyParams);
        }
        LinearLayout.LayoutParams soundParams = new LinearLayout.LayoutParams(dp(190), dp(54));
        soundParams.topMargin = dp(16);
        page.addView(button(profile.soundEnabled ? "Turn sound off" : "Turn sound on",
                v -> saveConfiguration(profile, profile.memoryMb, profile.cpuCore,
                        !profile.soundEnabled)), soundParams);
        if (machineStore.isWindowsInstaller(profile)) {
            boolean installer = machineStore.bootsInstaller(profile);
            LinearLayout.LayoutParams bootParams = new LinearLayout.LayoutParams(dp(260), dp(54));
            bootParams.topMargin = dp(16);
            page.addView(button(installer ? "Boot Windows disk" : "Boot installer", v -> {
                try {
                    machineStore.setWindowsInstallerBoot(profile, !installer);
                    showSettings(profile);
                } catch (IOException error) {
                    Toast.makeText(this, "The boot source could not be changed.",
                            Toast.LENGTH_LONG).show();
                }
            }), bootParams);
            LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(dp(260), dp(54));
            utilityParams.topMargin = dp(16);
            page.addView(button("Boot utility disk", v -> pickWindowsUtility(profile)), utilityParams);
        }
        Button delete = button("Delete machine", v -> confirmDeleteMachine(profile));
        delete.setTextColor(TEXT);
        delete.setBackground(background(Color.rgb(201, 78, 72), 12));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(210), dp(54));
        deleteParams.topMargin = dp(30);
        page.addView(delete, deleteParams);
        setContentView(page);
    }

    private String configurationSummary(MachineProfile profile) {
        String cpu = profile.fixedCycles > 0 ? "Performance trial " + profile.fixedCycles +
                " cycles" : profile.cpuCore.equals("normal") ? "Compatibility CPU" :
                "Automatic CPU";
        return profile.memoryMb + " MB memory · " + cpu + " · Sound " +
                (profile.soundEnabled ? "on" : "off");
    }

    private void saveConfiguration(MachineProfile profile, int memoryMb, String cpuCore,
            boolean soundEnabled) {
        try {
            MachineProfile updated = machineStore.updateConfiguration(profile, memoryMb, cpuCore,
                    soundEnabled);
            showSettings(updated);
        } catch (IOException error) {
            Toast.makeText(this, "Settings could not be saved.", Toast.LENGTH_LONG).show();
        }
    }

    private void savePerformanceProfile(MachineProfile profile, int cycles) {
        try {
            MachineProfile updated = machineStore.updatePerformanceProfile(profile, cycles);
            showSettings(updated);
        } catch (IOException error) {
            Toast.makeText(this, "The performance profile could not be saved.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void createExperimentalCopy(MachineProfile profile) {
        if (copyInProgress) return;
        if (machineStore.hasInterruptedSession() || !machineStore.hasCleanGuestShutdown(profile.id)) {
            Toast.makeText(this, "Shut down the source machine normally before copying it.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        copyInProgress = true;
        showCopyProgress(profile);
        new Thread(() -> {
            try {
                MachineProfile copy = machineStore.createExperimentalCopy(profile,
                        (stage, completedBytes, totalBytes) -> handler.post(() ->
                                updateCopyProgress(stage, completedBytes, totalBytes)));
                handler.post(() -> {
                    copyInProgress = false;
                    Toast.makeText(this, copy.name + " is ready for performance trials.",
                            Toast.LENGTH_LONG).show();
                    showHome();
                });
            } catch (IOException error) {
                handler.post(() -> {
                    copyInProgress = false;
                    showSettings(profile);
                    Toast.makeText(this, "The experimental copy could not be created: " +
                            error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "RoboWindowsCopy").start();
    }

    private void confirmDeleteMachine(MachineProfile profile) {
        if (copyInProgress) {
            Toast.makeText(this, "Wait for the machine copy to finish first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete " + profile.name + "?")
                .setMessage("This permanently deletes this machine and its private disk. " +
                        "Other machines are not affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete machine", (dialog, which) -> deleteMachine(profile))
                .show();
    }

    private void deleteMachine(MachineProfile profile) {
        try {
            machineStore.deleteMachine(profile);
            showHome();
            Toast.makeText(this, profile.name + " was deleted.", Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "The machine could not be deleted: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showCopyProgress(MachineProfile profile) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(28), dp(22), dp(28), dp(22));
        page.setBackgroundColor(BG);
        TextView title = text("Creating " + profile.name + " - copy", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(title);
        TextView detail = text("Creating an independent disk. Keep RoboWindows open until this " +
                "finishes; your stable machine is not changed.", 16, MUTED);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(16);
        page.addView(detail, detailParams);
        copyProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        copyProgressBar.setMax(1000);
        copyProgressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        progressParams.topMargin = dp(30);
        page.addView(copyProgressBar, progressParams);
        copyProgressStatus = text("Preparing copy", 16, TEXT);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        page.addView(copyProgressStatus, statusParams);
        copyProgressPercent = text("0%", 14, MUTED);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        percentParams.topMargin = dp(6);
        page.addView(copyProgressPercent, percentParams);
        setContentView(page);
    }

    private void updateCopyProgress(String stage, long completedBytes, long totalBytes) {
        if (!copyInProgress || copyProgressBar == null) return;
        if (!stage.isEmpty() && copyProgressStatus != null) copyProgressStatus.setText(stage);
        int progress = (int) Math.min(1000L, completedBytes * 1000L / Math.max(1L, totalBytes));
        copyProgressBar.setProgress(progress);
        if (copyProgressPercent != null) copyProgressPercent.setText((progress / 10) + "%");
    }

    private void showAddMachine() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(28), dp(22), dp(28), dp(22));
        page.setBackgroundColor(BG);
        page.addView(button("Back", v -> showHome()), new LinearLayout.LayoutParams(dp(110), dp(46)));
        TextView title = text("Add machine", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(26);
        page.addView(title, titleParams);
        TextView prompt = text("Choose a system family", 16, MUTED);
        LinearLayout.LayoutParams promptParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        promptParams.topMargin = dp(10);
        page.addView(prompt, promptParams);
        LinearLayout choices = new LinearLayout(this);
        LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(dp(220), dp(58));
        choices.addView(button("DOS", v -> pickMedia("DOS")), choiceParams);
        LinearLayout.LayoutParams windowsParams = new LinearLayout.LayoutParams(dp(220), dp(58));
        windowsParams.leftMargin = dp(14);
        choices.addView(button("Windows", v -> pickMedia("Windows")), windowsParams);
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        choicesParams.topMargin = dp(24);
        page.addView(choices, choicesParams);
        setContentView(page);
    }

    private void pickMedia(String family) {
        pendingFamily = family;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_MEDIA);
    }

    private void pickWindowsUtility(MachineProfile profile) {
        pendingUtilityProfile = profile;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, WINDOWS_UTILITY);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == WINDOWS_UTILITY && pendingUtilityProfile != null) {
            MachineProfile profile = pendingUtilityProfile;
            pendingUtilityProfile = null;
            try {
                java.io.File media = machineStore.importAdditionalMedia(uri, profile.id);
                machineStore.setWindowsUtilityBoot(profile, media);
                showSettings(profile);
            } catch (IOException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == CHANGE_MEDIA && currentSessionProfile != null) {
            try {
                java.io.File media = machineStore.importAdditionalMedia(uri, currentSessionProfile.id);
                if (!NativeHost.changeMedia(media.getAbsolutePath())) {
                    Toast.makeText(this, "The running machine could not change media.",
                            Toast.LENGTH_LONG).show();
                }
            } catch (IOException error) {
                Toast.makeText(this, "The selected media could not be imported.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode != PICK_MEDIA || pendingFamily == null) return;
        try {
            machineStore.importMachine(uri, pendingFamily);
            showHome();
        } catch (IOException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSession(MachineProfile profile) {
        showSession(profile, false);
    }

    private void showSession(MachineProfile profile, boolean recoveryBoot) {
        try {
            profile = recoveryBoot ? machineStore.prepareRecoveryStart(profile) :
                    machineStore.prepareNormalStart(profile);
        } catch (IOException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        final MachineProfile sessionProfile = profile;
        recoverySession = recoveryBoot;
        recoveryGuestShutdownObserved = false;
        long generation = ++sessionGeneration;
        currentSessionProfile = sessionProfile;
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        GuestDisplayView guest = new GuestDisplayView(this, this::deviceHandle);
        sessionGuest = guest;
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(8));
        controls.setBackground(background(Color.argb(238, 25, 30, 38), 0));
        sessionControls = controls;
        controls.addView(button("Exit", v -> showHome()), new LinearLayout.LayoutParams(dp(110), dp(44)));
        TextView title = text(recoveryBoot ? "Disk check · " + sessionProfile.name :
                sessionProfile.name, 20, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams sessionTitleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sessionTitleParams.leftMargin = dp(16);
        controls.addView(title, sessionTitleParams);
        Button pause = button("Pause", null);
        pause.setOnClickListener(v -> {
            sessionPaused = !sessionPaused;
            releasePointerCaptureAndCancel();
            if (sessionPaused) {
                sessionUiState.pause(android.os.SystemClock.uptimeMillis());
            } else {
                sessionUiState.resume(android.os.SystemClock.uptimeMillis());
                scheduleControlsHide();
            }
            setActiveSessionPaused(sessionPaused);
            pause.setText(sessionPaused ? "Resume" : "Pause");
            syncSessionControls();
        });
        controls.addView(pause, new LinearLayout.LayoutParams(dp(130), dp(44)));
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(dp(130), dp(44));
        restartParams.leftMargin = dp(10);
        controls.addView(button("Restart", v -> {
            releasePointerAndShowControls();
            restartSession(sessionProfile);
            pause.setText("Pause");
        }), restartParams);
        LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(dp(170), dp(44));
        mediaParams.leftMargin = dp(10);
        controls.addView(button("Change media", v -> pickSessionMedia()), mediaParams);
        page.addView(guest, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP);
        page.addView(controls, controlsParams);
        setContentView(page);
        guest.requestFocus();
        sessionPaused = false;
        requestGuestAudioFocus();
        sessionActive = NativeHost.startSession(sessionProfile.launchPath,
                getFilesDir().getAbsolutePath(), RuntimeTimingPolicy.forExperimentalMachine(
                        sessionProfile.isExperimental()));
        if (!sessionActive) {
            showHome();
            Toast.makeText(this, "This machine could not start.", Toast.LENGTH_LONG).show();
            return;
        }
        sessionUiState.start(android.os.SystemClock.uptimeMillis());
        syncSessionControls();
        scheduleControlsHide();
        if (audioFocusPaused) setActiveSessionPaused(true);
        if (sessionActive && !transientDebugSession) machineStore.markSessionStarted(sessionProfile.id);
        handler.postDelayed(() -> confirmSessionStarted(sessionProfile, generation), 700);
    }

    private void showDynamicDiagnostic(MachineProfile profile, DynamicCyclePolicy dynamicPolicy) {
        final MachineProfile sessionProfile = profile;
        currentSessionProfile = profile;
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        TextView decoderResidency = text("Decoder residency: waiting…", 13, MUTED);
        DynamicTrialController controller = new DynamicTrialController(this,
                (status, error, residency) -> {
            if (residency != null) decoderResidency.setText(residency);
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                showHome();
            } else if (status == NativeHost.SESSION_STOPPED) {
                showHome();
            }
        });
        dynamicTrial = controller;
        GuestDisplayView guest = new GuestDisplayView(this, this::deviceHandle,
                new GuestDisplayView.SessionBridge() {
                    @Override public void setSurface(android.view.Surface surface) {
                        if (dynamicTrial == null) return;
                        runDynamicCommand(activeController -> activeController.setSurface(surface));
                    }
                    @Override public void pushMouse(MotionEvent event, int handle, boolean captured) {
                        if (dynamicTrial == null) return;
                        runDynamicCommand(activeController -> activeController.pushMouse(
                                    event.getActionMasked(),
                                    event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                                    event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
                                    event.getX(), event.getY(), event.getButtonState(),
                                    event.getActionButton(),
                                    event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                                    event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getSource(),
                                    handle, event.getEventTime() * 1_000_000L, captured));
                    }
                }, false);
        sessionGuest = guest;
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(8));
        controls.setBackground(background(Color.argb(238, 25, 30, 38), 0));
        sessionControls = controls;
        controls.addView(button("Stop trial", v -> showHome()),
                new LinearLayout.LayoutParams(dp(130), dp(44)));
        TextView title = text(dynamicPolicy.label + " · " + sessionProfile.name, 20, PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout diagnosticLabels = new LinearLayout(this);
        diagnosticLabels.setOrientation(LinearLayout.VERTICAL);
        diagnosticLabels.addView(title);
        diagnosticLabels.addView(decoderResidency);
        controls.addView(diagnosticLabels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(guest, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP));
        LinearLayout readiness = new LinearLayout(this);
        readiness.setGravity(Gravity.CENTER_VERTICAL);
        readiness.setPadding(dp(12), dp(8), dp(12), dp(8));
        readiness.setBackground(background(Color.argb(238, 25, 30, 38), 0));
        TextView readinessLabel = text("Confirm only after Windows responds", 14, MUTED);
        readiness.addView(readinessLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button desktopReady = button("Desktop", null);
        desktopReady.setOnClickListener(v -> confirmDynamicReadiness(controller,
                DynamicReadiness.DESKTOP, desktopReady));
        readiness.addView(desktopReady, new LinearLayout.LayoutParams(dp(130), dp(44)));
        Button keyboardReady = button("Keyboard", null);
        keyboardReady.setOnClickListener(v -> confirmDynamicReadiness(controller,
                DynamicReadiness.KEYBOARD, keyboardReady));
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(dp(130), dp(44));
        keyboardParams.leftMargin = dp(10);
        readiness.addView(keyboardReady, keyboardParams);
        Button mouseReady = button("Mouse", null);
        mouseReady.setOnClickListener(v -> confirmDynamicReadiness(controller,
                DynamicReadiness.CAPTURED_MOUSE, mouseReady));
        LinearLayout.LayoutParams mouseParams = new LinearLayout.LayoutParams(dp(130), dp(44));
        mouseParams.leftMargin = dp(10);
        readiness.addView(mouseReady, mouseParams);
        page.addView(readiness, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.BOTTOM));
        setContentView(page);
        guest.requestFocus();
        sessionPaused = false;
        sessionActive = true;
        sessionUiState.start(android.os.SystemClock.uptimeMillis());
        syncSessionControls();
        // A diagnostic must always leave its explicit stop control visible.
        requestGuestAudioFocus();
        guest.post(() -> {
            if (!sessionActive || dynamicTrial != controller) return;
            try {
                controller.start(sessionProfile, guest.getHolder().getSurface(), dynamicPolicy);
                if (audioFocusPaused) controller.setPaused(true);
            } catch (IOException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                showHome();
            }
        });
    }

    private void confirmDynamicReadiness(DynamicTrialController controller, int evidence,
            Button button) {
        if (dynamicTrial != controller || !sessionActive) return;
        try {
            if ((controller.readinessMask() & evidence) != 0) return;
            controller.confirmReadiness(evidence);
            button.setText("✓ " + button.getText());
            button.setTextColor(PRIMARY);
            button.setBackground(background(SURFACE_HIGH, 12));
            if (DynamicReadiness.complete(controller.readinessMask())) {
                Toast.makeText(this, "Desktop, keyboard, and captured mouse are confirmed.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pickSessionMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, CHANGE_MEDIA);
    }

    private void restartSession(MachineProfile profile) {
        sessionPaused = false;
        currentSessionProfile = profile;
        if (dynamicTrial != null) {
            runDynamicCommand(DynamicTrialController::restart);
        } else if (!NativeHost.restartSession()) {
            Toast.makeText(this, "This machine could not restart.", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmSessionStarted(MachineProfile profile, long generation) {
        if (!isCurrentSession(profile, generation)) return;
        int status = NativeHost.sessionStatus();
        if (status == NativeHost.SESSION_RUNNING) {
            if (!transientDebugSession) machineStore.markBooted(profile);
            handler.postDelayed(() -> monitorSession(profile, generation), 250);
        } else if (status == NativeHost.SESSION_FAILED) {
            stopActiveSession();
            showHome();
            Toast.makeText(this, "This machine could not start.", Toast.LENGTH_LONG).show();
        } else if (status == NativeHost.SESSION_STARTING) {
            handler.postDelayed(() -> confirmSessionStarted(profile, generation), 700);
        }
    }

    private void monitorSession(MachineProfile profile, long generation) {
        if (!isCurrentSession(profile, generation)) return;
        int status = NativeHost.sessionStatus();
        if (status == NativeHost.SESSION_GUEST_SHUTDOWN) {
            if (recoverySession) recoveryGuestShutdownObserved = true;
            if (!transientDebugSession && !recoverySession) {
                machineStore.markGuestShutdown(profile.id);
            }
            showHome();
            return;
        }
        if (status == NativeHost.SESSION_FAILED || status == NativeHost.SESSION_STOPPED) {
            stopActiveSession();
            showHome();
            Toast.makeText(this, "This machine stopped unexpectedly.", Toast.LENGTH_LONG).show();
            return;
        }
        handler.postDelayed(() -> monitorSession(profile, generation), 250);
    }

    private boolean isCurrentSession(MachineProfile profile, long generation) {
        return sessionActive && currentSessionProfile == profile && sessionGeneration == generation;
    }

    private void stopActiveSession() {
        ++sessionGeneration;
        releasePointerCaptureAndCancel();
        sessionUiState.exit();
        handler.removeCallbacks(hideSessionControls);
        handler.removeCallbacks(captureRequestTimeout);
        boolean wasActive = sessionActive;
        DynamicTrialController trial = dynamicTrial;
        sessionActive = false;
        dynamicTrial = null;
        if (wasActive) {
            if (trial != null) {
                trial.stop();
                trial.destroy();
            } else {
                NativeHost.stopSession();
                if (recoverySession && recoveryGuestShutdownObserved) {
                    try {
                        machineStore.closeRecoveryAfterCleanShutdown(currentSessionProfile);
                        dynamicRecoveryApplied = false;
                    } catch (IOException error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else if (!transientDebugSession) machineStore.markSessionStopped();
            }
        }
        sessionPaused = false;
        currentSessionProfile = null;
        sessionGuest = null;
        sessionControls = null;
        consumingRevealTouch = false;
        transientDebugSession = false;
        recoverySession = false;
        recoveryGuestShutdownObserved = false;
        audioFocusPaused = false;
        if (audioManager != null && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
    }

    private boolean requestGuestAudioFocus() {
        if (audioManager == null) return false;
        if (audioFocusRequest == null) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(audioFocusListener, handler)
                    .build();
        }
        int result = audioManager.requestAudioFocus(audioFocusRequest);
        audioFocusPaused = result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return !audioFocusPaused;
    }

    private void showDiagnostics() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(28), dp(20), dp(28), dp(20));
        page.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("Back", v -> showHome()), new LinearLayout.LayoutParams(dp(100), dp(46)));
        TextView title = text("Input diagnostics", 26, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = dp(18);
        header.addView(title, titleParams);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout devices = card();
        diagnosticDevices = devices;
        populateDevices(devices);
        scroll.addView(devices);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(18);
        page.addView(scroll, scrollParams);
        setContentView(page);
        page.setFocusableInTouchMode(true);
        page.requestFocus();
        handler.post(refreshInputStats);
    }

    private void populateDevices(LinearLayout devices) {
        devices.removeAllViews();
        StringBuilder report = new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual() || !device.isExternal()) continue;
            InputDeviceRecord record = InputDeviceRecord.from(device, deviceHandle(id));
            report.append(record.capabilitySummary(deviceType(device))).append("\n\n");
        }
        if (report.length() == 0) report.append("No external input devices found.");
        devices.addView(text(report.toString().trim(), 15, TEXT));
        diagnosticStats = text(NativeHost.inputStats(), 14, MUTED);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dp(18);
        devices.addView(diagnosticStats, statsParams);
    }

    private void refreshDevices() {
        if (diagnosticDevices != null) populateDevices(diagnosticDevices);
    }

    private int deviceHandle(int androidId) {
        Integer existing = deviceHandles.get(androidId);
        if (existing != null) return existing;
        int handle = nextDeviceHandle++;
        deviceHandles.put(androidId, handle);
        return handle;
    }

    private boolean isExternalPhysical(InputDevice device) {
        return device != null && device.isExternal() && !device.isVirtual();
    }

    private boolean runDynamicCommand(DynamicCommand command) {
        DynamicTrialController controller = dynamicTrial;
        if (controller == null) return false;
        try {
            command.run(controller);
        } catch (IOException error) {
            Toast.makeText(this, "Dynamic runner connection failed. The copy remains quarantined.",
                    Toast.LENGTH_LONG).show();
            handler.post(this::showHome);
        }
        return true;
    }

    private void setActiveSessionPaused(boolean paused) {
        if (!runDynamicCommand(controller -> controller.setPaused(paused))) {
            NativeHost.setPaused(paused);
        }
    }

    private void cancelActiveSessionInput() {
        if (!runDynamicCommand(DynamicTrialController::cancelInput)) NativeHost.cancelInput();
    }

    private void pushSessionKey(int action, int keyCode, int scanCode, int repeatCount,
            int metaState, int source, int device, long eventNanos) {
        if (!runDynamicCommand(controller -> controller.pushKey(action, keyCode, scanCode,
                repeatCount, metaState, source, device, eventNanos))) {
            NativeHost.pushKey(action, keyCode, scanCode, repeatCount, metaState, source, device,
                    eventNanos);
        }
    }

    private void pushSessionTouch(MotionEvent event) {
        int handle = deviceHandle(event.getDeviceId());
        if (!runDynamicCommand(controller -> controller.pushTouch(event.getActionMasked(),
                event.getPointerCount(), event.getX(), event.getY(), event.getPressure(),
                event.getSource(), handle, event.getEventTime() * 1_000_000L))) {
            NativeHost.pushTouch(event.getActionMasked(), event.getPointerCount(), event.getX(),
                    event.getY(), event.getPressure(), event.getSource(), handle,
                    event.getEventTime() * 1_000_000L);
        }
    }

    private String deviceType(InputDevice device) {
        int sources = device.getSources();
        if ((sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) return "Mouse";
        if ((sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) return "Keyboard";
        if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) return "Controller";
        return "Input device";
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isExternalPhysical(event.getDevice())) return super.dispatchKeyEvent(event);
        pushSessionKey(event.getAction(), event.getKeyCode(), event.getScanCode(),
                event.getRepeatCount(), event.getMetaState(), event.getSource(),
                deviceHandle(event.getDeviceId()), event.getEventTime() * 1_000_000L);
        if (sessionActive) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            if (!isExternalPhysical(event.getDevice())) return super.dispatchGenericMotionEvent(event);
            if (sessionActive) {
                if (!pointerCaptured && event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS &&
                        isInside(event, sessionGuest) &&
                        (!sessionUiState.areControlsVisible() || !isInside(event, sessionControls))) {
                    requestSessionPointerCapture();
                    return true;
                }
                // Captured events are delivered only through GuestDisplayView.
                return super.dispatchGenericMotionEvent(event);
            }
            // The diagnostics screen observes uncaptured mouse data but no running guest does.
            NativeHost.pushMouse(event.getActionMasked(),
                    event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                    event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
                    event.getX(), event.getY(), event.getButtonState(), event.getActionButton(),
                    event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getSource(),
                    deviceHandle(event.getDeviceId()), event.getEventTime() * 1_000_000L, false);
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (consumingRevealTouch) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) consumingRevealTouch = false;
            return true;
        }
        if (sessionActive && event.getActionMasked() == MotionEvent.ACTION_DOWN &&
                event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) && event.getY() <= dp(32) &&
                revealSessionControls()) {
            consumingRevealTouch = true;
            return true;
        }
        pushSessionTouch(event);
        // Touch remains Android UI input until an explicit guest-touch policy is selected.
        return super.dispatchTouchEvent(event);
    }

    @Override public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
        pointerCaptured = hasCapture;
        handler.removeCallbacks(captureRequestTimeout);
        boolean failed = sessionUiState.pointerCaptureChanged(hasCapture,
                android.os.SystemClock.uptimeMillis());
        if (hasCapture && !sessionUiState.isCaptured()) {
            getWindow().getDecorView().releasePointerCapture();
            pointerCaptured = false;
        }
        if (!hasCapture) cancelActiveSessionInput();
        syncSessionControls();
        if (failed) Toast.makeText(this,
                "Mouse capture is unavailable. Controls remain available.",
                Toast.LENGTH_LONG).show();
    }

    @Override public void onBackPressed() {
        if (sessionActive && revealSessionControls()) return;
        if (sessionActive) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onPause() {
        appPaused = true;
        releasePointerCaptureAndCancel();
        sessionUiState.pause(android.os.SystemClock.uptimeMillis());
        syncSessionControls();
        if (sessionActive) setActiveSessionPaused(true);
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        appPaused = false;
        if (sessionActive) requestGuestAudioFocus();
        if (sessionActive) {
            if (sessionPaused || audioFocusPaused) {
                sessionUiState.pause(android.os.SystemClock.uptimeMillis());
            } else {
                sessionUiState.resume(android.os.SystemClock.uptimeMillis());
                scheduleControlsHide();
            }
            syncSessionControls();
        }
        if (sessionActive && !sessionPaused && !audioFocusPaused) {
            setActiveSessionPaused(false);
        }
    }

    @Override protected void onDestroy() {
        ((InputManager) getSystemService(Context.INPUT_SERVICE))
                .unregisterInputDeviceListener(inputListener);
        handler.removeCallbacksAndMessages(null);
        cancelCpuDiagnostic();
        stopActiveSession();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!sessionActive) return;
        if (!hasFocus) {
            releasePointerCaptureAndCancel();
            sessionUiState.pause(android.os.SystemClock.uptimeMillis());
            syncSessionControls();
            setActiveSessionPaused(true);
        } else if (!appPaused && !sessionPaused && !audioFocusPaused) {
            sessionUiState.resume(android.os.SystemClock.uptimeMillis());
            syncSessionControls();
            scheduleControlsHide();
            setActiveSessionPaused(false);
        }
    }

    private boolean isInside(MotionEvent event, View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0] && x < location[0] + view.getWidth() &&
                y >= location[1] && y < location[1] + view.getHeight();
    }

    private void requestSessionPointerCapture() {
        if (sessionGuest == null ||
                !sessionUiState.requestCapture(android.os.SystemClock.uptimeMillis())) return;
        sessionGuest.requestFocus();
        try {
            sessionGuest.requestPointerCapture();
            handler.removeCallbacks(captureRequestTimeout);
            handler.postDelayed(captureRequestTimeout, SessionUiState.CAPTURE_REQUEST_TIMEOUT_MS);
        } catch (IllegalStateException error) {
            sessionUiState.pointerCaptureChanged(false, android.os.SystemClock.uptimeMillis());
            syncSessionControls();
            Toast.makeText(this, "Mouse capture is unavailable. Controls remain available.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean revealSessionControls() {
        if (!sessionUiState.revealFromUser(android.os.SystemClock.uptimeMillis())) return false;
        releasePointerCaptureAndCancel();
        syncSessionControls();
        scheduleControlsHide();
        return true;
    }

    private void releasePointerAndShowControls() {
        if (!sessionActive) {
            releasePointerCaptureAndCancel();
            return;
        }
        sessionUiState.releaseAndShow(android.os.SystemClock.uptimeMillis());
        releasePointerCaptureAndCancel();
        syncSessionControls();
        scheduleControlsHide();
    }

    private void releasePointerCaptureAndCancel() {
        handler.removeCallbacks(captureRequestTimeout);
        if (pointerCaptured || (sessionGuest != null && sessionGuest.hasPointerCapture())) {
            getWindow().getDecorView().releasePointerCapture();
        }
        pointerCaptured = false;
        cancelActiveSessionInput();
    }

    private void scheduleControlsHide() {
        handler.removeCallbacks(hideSessionControls);
        if (dynamicTrial != null) return;
        handler.postDelayed(hideSessionControls, SessionUiState.CONTROLS_VISIBLE_MS);
    }

    private void syncSessionControls() {
        if (sessionControls != null) sessionControls.setVisibility(dynamicTrial != null ||
                sessionUiState.areControlsVisible() ? View.VISIBLE : View.GONE);
    }
}
