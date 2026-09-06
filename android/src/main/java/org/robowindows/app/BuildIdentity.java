package org.robowindows.app;

final class BuildIdentity {
    private BuildIdentity() {}

    static String label(String versionName, String sourceRevision) {
        String version = versionName == null || versionName.isEmpty() ? "unknown" : versionName;
        String revision = sourceRevision == null || sourceRevision.isEmpty() ?
                "unknown" : sourceRevision;
        return "v" + version + " · " + revision;
    }
}
