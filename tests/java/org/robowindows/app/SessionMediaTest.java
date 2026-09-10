package org.robowindows.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SessionMediaTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        SessionMediaRequest request = new SessionMediaRequest();
        require(!request.consume(0, true), "no unsolicited result");
        request.begin(1);
        require(request.consume(1, true), "current picker accepted");
        require(!request.consume(1, true), "one-shot result");
        request.begin(2);
        require(!request.consume(3, true), "different session rejected");
        request.begin(3);
        require(!request.consume(3, false), "stopped session rejected");
        request.begin(4); request.clear();
        require(!request.consume(4, true), "cancel/restart invalidates picker");
        Path root = Files.createTempDirectory("robowindows-media-");
        try {
            File media = Files.createDirectory(root.resolve("media")).toFile();
            Path owned = Files.write(media.toPath().resolve("owned.iso"), new byte[]{1, 2});
            Path other = Files.write(root.resolve("other.iso"), new byte[]{3, 4});
            Path symlink = Files.createSymbolicLink(media.toPath().resolve("alias.iso"), other);
            String[] registered = {owned.toString(), other.toString(), symlink.toString()};
            require(OwnedMediaPath.require(media, owned.toString(), registered).equals(owned.toFile()), "owned attachment");
            require(OwnedMediaPath.requireWithin(media, owned.toString()).equals(owned.toFile()), "child confinement");
            for (String invalid : new String[]{null, other.toString(), symlink.toString(), media.getPath(),
                    new File(media, "missing.iso").getPath(), new File(media, "../media/owned.iso").getPath()}) {
                boolean rejected = false;
                try { OwnedMediaPath.require(media, invalid, registered); }
                catch (IOException expected) { rejected = true; }
                require(rejected, "outside/missing/alias/directory media rejected");
            }
            boolean rejected = false;
            try { OwnedMediaPath.require(media, owned.toString(), new String[0]); }
            catch (IOException expected) { rejected = true; }
            require(rejected, "unregistered attachment rejected");
            File aliasedDirectory = Files.createSymbolicLink(root.resolve("aliased-media"), media.toPath()).toFile();
            rejected = false;
            try { OwnedMediaPath.requireWithin(aliasedDirectory, owned.toString()); }
            catch (IOException expected) { rejected = true; }
            require(rejected, "aliased directory rejected");
            Path platformAlias = Files.createSymbolicLink(root.resolve("platform-root"), root);
            File platformMedia = platformAlias.resolve("media").toFile();
            String platformPath = new File(platformMedia, "owned.iso").getPath();
            require(OwnedMediaPath.require(platformMedia, platformPath, new String[]{platformPath})
                    .equals(owned.toFile()), "platform ancestor alias accepted for registered media");
            require(OwnedMediaPath.requireWithin(platformMedia, owned.toString()).equals(owned.toFile()),
                    "child accepts canonical parent handoff across platform alias");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new)) Files.delete(path);
            }
        }
        System.out.println("Session-bound picker and owned-media boundary checks passed");
    }
}
