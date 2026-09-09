package org.robowindows.app;

/** Exact allowlist for the legacy source-owned direct-core smoke fixture. */
final class DisposableCoreConfig {
    private DisposableCoreConfig() {}
    static String expected(String disk) {
        if (disk == null || disk.contains("\"") || disk.contains("\n") || disk.contains("\r")) {
            throw new IllegalArgumentException("Invalid fixture path");
        }
        return "[dosbox]\nmemsize=16\n[cpu]\ncore=normal\n[mixer]\nnosound=true\n" +
                "[autoexec]\n@echo off\nboot \"" + disk + "\"\n";
    }
}
