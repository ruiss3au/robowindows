package org.robowindows.app;

public final class BuildIdentityTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(BuildIdentity.label("0.1.0-dev-debug", "abc123def456")
                        .equals("v0.1.0-dev-debug · abc123def456"),
                "clean build identity is malformed");
        require(BuildIdentity.label("0.1.0-dev-debug", "abc123def456+dirty")
                        .endsWith("abc123def456+dirty"),
                "dirty build marker is missing");
        require(BuildIdentity.label(null, "").equals("vunknown · unknown"),
                "unknown identity fallback is malformed");
    }
}
