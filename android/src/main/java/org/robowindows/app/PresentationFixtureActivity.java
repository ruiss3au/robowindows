package org.robowindows.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.util.Log;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Non-exported isolated process; consumes only the packaged redistributable fixture. */
public final class PresentationFixtureActivity extends Activity {
    private final Handler handler = new Handler();
    private File image, launch, directory;
    private boolean started, finished;
    private long began;
    private String core;
    private String workload;
    private int presentation;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        MachineStore store = new MachineStore(this);
        core = getIntent().getStringExtra("core");
        workload = getIntent().getStringExtra("workload");
        if (workload == null) workload = "tone";
        presentation = getIntent().getIntExtra("presentation", -1);
        if (!BuildConfig.DEBUG || store.hasInterruptedSession() ||
                !("normal".equals(core) || "dynamic".equals(core)) ||
                !PresentationPolicy.allowed(presentation) ||
                !(PresentationWorkload.allowed(workload) || CacheWorkload.allowed(workload)) ||
                !CpuFixtureGate.passed(this)) {
            complete("FAIL invalid fixture start"); return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            directory = new File(getCacheDir(), "presentation-" + java.util.UUID.randomUUID());
            if (!directory.mkdir()) throw new java.io.IOException("fixture directory");
            image = new File(directory, "fixture.ima"); launch = new File(directory, "launch.conf");
            int resource = "stress".equals(workload) ? R.raw.robowindows_stress_tone : R.raw.robowindows_gpu_tone;
            switch (CacheWorkload.id(workload)) {
                case 1: resource = R.raw.robowindows_cache_warm; break;
                case 2: resource = R.raw.robowindows_cache_cold; break;
                case 3: resource = R.raw.robowindows_cache_reuse; break;
                case 4: resource = R.raw.robowindows_cache_data; break;
                case 5: resource = R.raw.robowindows_cache_rewrite; break;
                default: break;
            }
            try (InputStream input = getResources().openRawResource(resource);
                    FileOutputStream output = new FileOutputStream(image)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.getFD().sync();
            }
            if (image.length() != 1474560) throw new java.io.IOException("fixture size");
            String config = "[dosbox]\nmemsize=16\n[cpu]\ncore=" + core +
                    "\ncycles=fixed 20000\ncputype=pentium_slow\n[mixer]\nnosound=false" +
                    "\n[speaker]\npcspeaker=true\n[autoexec]\n@echo off\nboot \"" + image.getPath() + "\"\n";
            try (FileOutputStream output = new FileOutputStream(launch)) {
                output.write(config.getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
            }
        } catch (Exception error) { complete("FAIL fixture preparation"); return; }
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) { NativeHost.setSurface(holder.getSurface()); }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                NativeHost.setSurface(holder.getSurface());
                if (started || finished) return;
                audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                audioFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(change -> {
                            if (change != AudioManager.AUDIOFOCUS_GAIN && !finished) complete("FAIL audio focus lost");
                        }).build();
                if (audioManager.requestAudioFocus(audioFocus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    complete("FAIL audio focus unavailable"); return;
                }
                began = SystemClock.elapsedRealtime();
                started = NativeHost.startSession(launch.getPath(), getFilesDir().getPath(),
                        RuntimeTimingPolicy.BALANCED_100_MS, presentation);
                if (!started) { complete("FAIL native start"); return; }
                handler.postDelayed(PresentationFixtureActivity.this::poll, 100);
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                NativeHost.setSurface(null);
                if (!finished) complete("FAIL surface lost");
            }
        });
        setContentView(view);
    }

    private void poll() {
        if (finished) return;
        long now = SystemClock.elapsedRealtime();
        if (CacheWorkload.allowed(workload)) { pollCache(now); return; }
        if (now - began < 122000) { handler.postDelayed(this::poll, 100); return; }
        final String decoder = NativeHost.sessionDecoder();
        final int activePresentation = NativeHost.sessionPresentation();
        // Core disk writes are buffered. Unload this disposable guest before reading.
        NativeHost.stopSession(); started = false;
        try (RandomAccessFile file = new RandomAccessFile(image, "r")) {
            file.seek(17 * 512L);
            byte[] record = new byte[32]; file.readFully(record);
            long ticks = PresentationWorkload.ticks(workload, record);
            if (ticks > 0) {
                long hostMs = now - began;
                double guestMs = ticks * 65536000.0 / 1193182.0;
                boolean good = hostMs >= 122000 && hostMs <= 124000 && Math.abs(guestMs / hostMs - 1) <= .05 &&
                        activePresentation == presentation && ("dynamic".equals(core) ? "DynRec" : "Normal").equals(decoder);
                complete((good ? "PASS" : "FAIL") + " timer core=" + core + " presentation=" + presentation +
                        " host_ms=" + hostMs + " guest_ms=" + Math.round(guestMs) +
                        " decoder=" + decoder + " workload=" + workload +
                        ("stress".equals(workload) ? " phase_mask=15" : "")); return;
            }
        } catch (Exception error) { complete("FAIL result read"); return; }
        complete("FAIL missing guest timer record");
    }

    private void pollCache(long now) {
        if (now - began >= 60000) { complete("FAIL cache timeout"); return; }
        int status = NativeHost.sessionStatus();
        if (status != NativeHost.SESSION_GUEST_SHUTDOWN) {
            if (status == NativeHost.SESSION_FAILED || status == NativeHost.SESSION_STOPPED) {
                complete("FAIL cache unexpected native exit"); return;
            }
            handler.postDelayed(this::poll, 100); return;
        }
        String decoder = NativeHost.sessionDecoder();
        // Join cleanup: the shutdown notification can precede buffered disk flush.
        NativeHost.stopSession(); started = false;
        try (RandomAccessFile file = new RandomAccessFile(image, "r")) {
            file.seek(17 * 512L);
            byte[] bytes = new byte[64]; file.readFully(bytes);
            CacheWorkload.Result result = CacheWorkload.parse(workload, bytes);
            if (result == null || !("dynamic".equals(core) ? "DynRec" : "Normal").equals(decoder)) {
                complete("FAIL cache result or configured decoder"); return;
            }
            complete("PASS correctness core=" + core + " presentation=" + presentation +
                    " workload=" + workload + " host_ms=" + (now - began) +
                    " decoder=" + decoder + result.fields());
        } catch (Exception error) { complete("FAIL cache result read"); }
    }

    private void complete(String result) {
        if (finished) return;
        finished = true; handler.removeCallbacksAndMessages(null);
        if (started) NativeHost.stopSession();
        started = false;
        if (audioManager != null && audioFocus != null) audioManager.abandonAudioFocusRequest(audioFocus);
        boolean cleaned = true;
        if (launch != null && launch.exists() && !launch.delete()) cleaned = false;
        if (image != null && image.exists() && !image.delete()) cleaned = false;
        if (directory != null && directory.exists() && !directory.delete()) cleaned = false;
        if (CacheWorkload.allowed(workload) && !cleaned) result = "FAIL cache cleanup";
        Log.i(CacheWorkload.allowed(workload) ? "RoboWindowsCacheFixture" : "RoboWindowsPresentation", result);
        finish();
        IsolatedProcessExit.afterResult();
    }
    @Override protected void onPause() {
        super.onPause();
        if (started && !finished) complete("FAIL fixture backgrounded");
    }
    @Override protected void onDestroy() {
        if (!finished) complete("FAIL fixture destroyed");
        super.onDestroy();
    }
}
