package org.robowindows.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Accept only exact, registered, nonsymlink files in one machine's media directory. */
final class OwnedMediaPath {
    private OwnedMediaPath() {}
    static File require(File mediaDirectory, String path, String[] registered) throws IOException {
        if (path == null || registered == null) throw new IOException("Media selection is missing");
        File canonical = requireWithin(mediaDirectory, path);
        for (String owned : registered) {
            if (new File(path).getAbsolutePath().equals(owned) || canonical.getPath().equals(owned)) return canonical;
        }
        throw new IOException("Media is not registered to this machine");
    }

    static File requireWithin(File mediaDirectory, String path) throws IOException {
        if (path == null) throw new IOException("Media selection is missing");
        File directory = mediaDirectory.getCanonicalFile();
        File supplied = new File(path).getAbsoluteFile();
        File canonical = supplied.getCanonicalFile();
        File absoluteDirectory = mediaDirectory.getAbsoluteFile();
        // Android can canonicalize an app-owned storage ancestor differently.
        // Accept that platform alias, never an alias of the media directory/file
        // itself or a path containing traversal components.
        if (Files.isSymbolicLink(absoluteDirectory.toPath()) ||
                !absoluteDirectory.toPath().normalize().equals(absoluteDirectory.toPath()) ||
                !supplied.toPath().normalize().equals(supplied.toPath()) ||
                (!absoluteDirectory.equals(supplied.getParentFile()) &&
                        !directory.equals(supplied.getParentFile())) ||
                Files.isSymbolicLink(supplied.toPath()) ||
                !directory.equals(canonical.getParentFile()) ||
                !canonical.isFile() || !canonical.canRead()) {
            throw new IOException("Media is outside this machine's imported storage");
        }
        return canonical;
    }
}
