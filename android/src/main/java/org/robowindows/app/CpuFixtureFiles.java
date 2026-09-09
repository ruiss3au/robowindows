package org.robowindows.app;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Creates and reads only disposable, project-owned CPU fixture files. */
final class CpuFixtureFiles {
    static final long IMAGE_BYTES = 1_474_560;
    static final long LEGACY_RESULT_OFFSET = 17 * 512L;
    static final long EXPANDED_RESULT_OFFSET = 40 * 512L;

    static final class Run {
        final File image;
        final File launch;
        final ExpandedCpuSuite suite;

        Run(File image, File launch, ExpandedCpuSuite suite) {
            this.image = image;
            this.launch = launch;
            this.suite = suite;
        }
    }

    private CpuFixtureFiles() {}

    static Run create(Context context, String mode, ExpandedCpuSuite suite) throws IOException {
        if (!"normal".equals(mode) && !"dynamic".equals(mode)) {
            throw new IOException("Unsupported CPU fixture mode");
        }
        String fixtureName = suite == null ? "cpu-fixture-v3" : "x86-gate-v4";
        String runName = suite == null ? mode : mode + "-" + Integer.toHexString(suite.id);
        File root = new File(context.getCacheDir(), fixtureName).getCanonicalFile();
        File directory = new File(root, runName).getCanonicalFile();
        if (!root.equals(directory.getParentFile())) {
            throw new IOException("CPU fixture directory is invalid");
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create CPU fixture directory");
        }
        File image = new File(directory, "fixture.ima").getCanonicalFile();
        File launch = new File(directory, "launch.conf").getCanonicalFile();
        if (!directory.equals(image.getParentFile()) || !directory.equals(launch.getParentFile())) {
            throw new IOException("CPU fixture path escaped its cache directory");
        }
        if (image.exists() && !image.delete()) throw new IOException("Cannot reset CPU fixture image");
        if (launch.exists() && !launch.delete()) throw new IOException("Cannot reset CPU fixture launch");

        int resource = suite == null ? R.raw.robowindows_cpu_v3 : resourceFor(suite);
        try (InputStream input = context.getResources().openRawResource(resource);
                FileOutputStream output = new FileOutputStream(image)) {
            byte[] buffer = new byte[8192];
            long copied = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                copied += read;
                if (copied > IMAGE_BYTES) {
                    throw new IOException("Packaged CPU fixture image is too large");
                }
                output.write(buffer, 0, read);
            }
            if (copied != IMAGE_BYTES) {
                throw new IOException("Packaged CPU fixture image has the wrong size");
            }
            output.getFD().sync();
        }

        String escaped = image.getAbsolutePath().replace("\\", "\\\\")
                .replace("\"", "\\\"");
        String config = "[dosbox]\nmemsize=16\n[cpu]\ncore=" + mode +
                "\ncycles=fixed 20000\ncputype=pentium_slow\n[mixer]\nnosound=true" +
                "\n[autoexec]\n@echo off\nboot \"" + escaped + "\"\n";
        try (FileOutputStream output = new FileOutputStream(launch)) {
            output.write(config.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        return new Run(image, launch, suite);
    }

    static byte[] readResult(Run run) throws IOException {
        if (run.image.length() != IMAGE_BYTES) throw new IOException("CPU fixture image size changed");
        int recordBytes = run.suite == null ? CpuFixtureResult.RECORD_BYTES :
                ExpandedCpuResult.RECORD_BYTES;
        long resultOffset = run.suite == null ? LEGACY_RESULT_OFFSET :
                EXPANDED_RESULT_OFFSET;
        byte[] record = new byte[recordBytes];
        try (RandomAccessFile image = new RandomAccessFile(run.image, "r")) {
            image.seek(resultOffset);
            image.readFully(record);
        }
        return run.suite == null ? CpuFixtureResult.parse(record).record :
                ExpandedCpuResult.parse(record).record;
    }

    static void remove(Run run) {
        if (run == null) return;
        run.launch.delete();
        run.image.delete();
        File directory = run.image.getParentFile();
        if (directory != null) {
            directory.delete();
            File root = directory.getParentFile();
            if (root != null) root.delete();
        }
    }

    private static int resourceFor(ExpandedCpuSuite suite) {
        switch (suite) {
            case STRINGS: return R.raw.robowindows_x86_gate_strings;
            case FAULT_RETRY: return R.raw.robowindows_x86_gate_fault_retry;
            case INTEGER_FLAGS: return R.raw.robowindows_x86_gate_integer_flags;
            case STACK_CONTROL: return R.raw.robowindows_x86_gate_stack_control;
            case PAGING_SMC: return R.raw.robowindows_x86_gate_paging_smc;
            case X87: return R.raw.robowindows_x86_gate_x87;
            case MIXED_SEEDS: return R.raw.robowindows_x86_gate_mixed_seeds;
            default: throw new IllegalArgumentException("Unknown expanded CPU suite");
        }
    }
}
