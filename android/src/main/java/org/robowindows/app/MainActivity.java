package org.robowindows.app;

import android.app.Activity;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.format.DateUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 19, 24);
    private static final int SURFACE = Color.rgb(25, 30, 38);
    private static final int SURFACE_HIGH = Color.rgb(34, 42, 53);
    private static final int PRIMARY = Color.rgb(117, 213, 181);
    private static final int TEXT = Color.rgb(238, 243, 247);
    private static final int MUTED = Color.rgb(157, 170, 183);
    private static final int PICK_MEDIA = 41;
    private static final int CHANGE_MEDIA = 42;
    private static final int WINDOWS_UTILITY = 43;

    private LinearLayout content;
    private boolean pointerCaptured;
    private final SessionUiState sessionUiState = new SessionUiState();
    private GuestDisplayView sessionGuest;
    private View sessionControls;
    private boolean consumingRevealTouch;
    private MachineStore machineStore;
    private String pendingFamily;
    private boolean sessionActive;
    private boolean sessionPaused;
    private MachineProfile currentSessionProfile;
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
                NativeHost.setPaused(false);
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            audioFocusPaused = true;
            releasePointerCaptureAndCancel();
            sessionUiState.pause(android.os.SystemClock.uptimeMillis());
            syncSessionControls();
            NativeHost.setPaused(true);
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
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        int keyCode = intent.getIntExtra("robowindows.testKey", -1);
        if (keyCode < 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        NativeHost.pushKey(KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0,
                InputDevice.SOURCE_KEYBOARD, 0, now * 1_000_000L);
        // Keep the key down across several guest input polls. An immediate down/up pair can be
        // entirely missed by a libretro core between frames.
        handler.postDelayed(() -> {
            long releasedAt = android.os.SystemClock.uptimeMillis();
            NativeHost.pushKey(KeyEvent.ACTION_UP, keyCode, 0, 0, 0,
                    InputDevice.SOURCE_KEYBOARD, 0, releasedAt * 1_000_000L);
        }, 75);
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
        LinearLayout.LayoutParams diagnosticParams = new LinearLayout.LayoutParams(dp(190), dp(46));
        diagnosticParams.topMargin = dp(28);
        rail.addView(button("Input test", v -> showDiagnostics()), diagnosticParams);

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
        String detail = profile.family;
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
        row.addView(button(startLabel, v -> showSession(profile)), startParams);
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
        LinearLayout.LayoutParams conservative = new LinearLayout.LayoutParams(dp(260), dp(54));
        conservative.leftMargin = dp(12);
        presets.addView(button("Windows experimental", v -> saveConfiguration(profile, 64, "auto",
                profile.soundEnabled)), conservative);
        LinearLayout.LayoutParams presetsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetsParams.topMargin = dp(24);
        page.addView(presets, presetsParams);
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
        setContentView(page);
    }

    private String configurationSummary(MachineProfile profile) {
        String cpu = profile.cpuCore.equals("normal") ? "Compatibility CPU" :
                "Experimental automatic CPU";
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
        currentSessionProfile = profile;
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
        TextView title = text(profile.name, 20, TEXT);
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
            NativeHost.setPaused(sessionPaused);
            pause.setText(sessionPaused ? "Resume" : "Pause");
            syncSessionControls();
        });
        controls.addView(pause, new LinearLayout.LayoutParams(dp(130), dp(44)));
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(dp(130), dp(44));
        restartParams.leftMargin = dp(10);
        controls.addView(button("Restart", v -> {
            releasePointerAndShowControls();
            restartSession(profile);
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
        sessionActive = NativeHost.startSession(profile.launchPath, getFilesDir().getAbsolutePath());
        if (!sessionActive) {
            showHome();
            Toast.makeText(this, "This machine could not start.", Toast.LENGTH_LONG).show();
            return;
        }
        sessionUiState.start(android.os.SystemClock.uptimeMillis());
        syncSessionControls();
        scheduleControlsHide();
        if (audioFocusPaused) NativeHost.setPaused(true);
        if (sessionActive && !transientDebugSession) machineStore.markSessionStarted(profile.id);
        handler.postDelayed(() -> confirmSessionStarted(profile), 700);
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
        if (!NativeHost.restartSession()) {
            Toast.makeText(this, "This machine could not restart.", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmSessionStarted(MachineProfile profile) {
        if (!sessionActive || currentSessionProfile != profile) return;
        int status = NativeHost.sessionStatus();
        if (status == 2) {
            if (!transientDebugSession) machineStore.markBooted(profile);
        } else if (status == 3) {
            stopActiveSession();
            showHome();
            Toast.makeText(this, "This machine could not start.", Toast.LENGTH_LONG).show();
        } else if (status == 1) {
            handler.postDelayed(() -> confirmSessionStarted(profile), 700);
        }
    }

    private void stopActiveSession() {
        releasePointerCaptureAndCancel();
        sessionUiState.exit();
        handler.removeCallbacks(hideSessionControls);
        handler.removeCallbacks(captureRequestTimeout);
        if (sessionActive) {
            NativeHost.stopSession();
            if (!transientDebugSession) machineStore.markSessionStopped();
        }
        sessionActive = false;
        sessionPaused = false;
        currentSessionProfile = null;
        sessionGuest = null;
        sessionControls = null;
        consumingRevealTouch = false;
        transientDebugSession = false;
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

    private String deviceType(InputDevice device) {
        int sources = device.getSources();
        if ((sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) return "Mouse";
        if ((sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) return "Keyboard";
        if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) return "Controller";
        return "Input device";
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isExternalPhysical(event.getDevice())) return super.dispatchKeyEvent(event);
        NativeHost.pushKey(event.getAction(), event.getKeyCode(), event.getScanCode(),
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
        NativeHost.pushTouch(event.getActionMasked(), event.getPointerCount(), event.getX(),
                event.getY(), event.getPressure(), event.getSource(), deviceHandle(event.getDeviceId()),
                event.getEventTime() * 1_000_000L);
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
        if (!hasCapture) NativeHost.cancelInput();
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
        if (sessionActive) NativeHost.setPaused(true);
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
        if (sessionActive && !sessionPaused && !audioFocusPaused) NativeHost.setPaused(false);
    }

    @Override protected void onDestroy() {
        ((InputManager) getSystemService(Context.INPUT_SERVICE))
                .unregisterInputDeviceListener(inputListener);
        handler.removeCallbacksAndMessages(null);
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
        } else if (!appPaused && !sessionPaused && !audioFocusPaused) {
            sessionUiState.resume(android.os.SystemClock.uptimeMillis());
            syncSessionControls();
            scheduleControlsHide();
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
        NativeHost.cancelInput();
    }

    private void scheduleControlsHide() {
        handler.removeCallbacks(hideSessionControls);
        handler.postDelayed(hideSessionControls, SessionUiState.CONTROLS_VISIBLE_MS);
    }

    private void syncSessionControls() {
        if (sessionControls != null) sessionControls.setVisibility(
                sessionUiState.areControlsVisible() ? View.VISIBLE : View.GONE);
    }
}
