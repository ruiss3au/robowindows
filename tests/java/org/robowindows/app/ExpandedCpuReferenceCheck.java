package org.robowindows.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

/** Command-line validator used by the pinned QEMU reference runner. */
public final class ExpandedCpuReferenceCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != ExpandedCpuSuite.values().length) {
            throw new IllegalArgumentException("expected one record per expanded CPU suite");
        }
        EnumSet<ExpandedCpuSuite> seen = EnumSet.noneOf(ExpandedCpuSuite.class);
        for (String argument : args) {
            ExpandedCpuResult result = ExpandedCpuResult.parse(Files.readAllBytes(Path.of(argument)));
            if (!seen.add(result.suite)) {
                throw new IllegalStateException("duplicate reference suite: " + result.suite.label);
            }
        }
        if (seen.size() != ExpandedCpuSuite.values().length) {
            throw new IllegalStateException("reference suite set is incomplete");
        }
        System.out.println("Expanded CPU reference records passed");
    }
}
