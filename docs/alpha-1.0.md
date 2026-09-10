# RoboWindows 1.0 Alpha

This milestone makes ordinary DynRec sessions use the same translucent toolbar
as Normal: Exit, Pause/Resume, Restart, Change media and optional counters.
Controls hide after four seconds while running; Back or a top-edge touch reveals
them without resizing the guest. Paused/focus-lost/dialog/import states keep
controls available. Ordinary DynRec no longer has the bottom readiness bar;
explicit debug diagnostics retain their evidence controls without granting
readiness automatically.

DynRec media changes cross the isolated runtime boundary. Imports run off the UI
thread and remain bound to their originating session. Parent-side registration,
child-side canonical machine-media confinement and durable-attempt validation
prevent arbitrary path handoff. Invalid media reports an error without deliberately
stopping the guest. Live attachments preserve the active configuration generation
so they do not invalidate DynRec recovery; stopped-machine imports still advance it.

The version is `1.0-alpha`, versionCode 2; debug uses `1.0-alpha-debug` and retains
the existing debug package ID for data-preserving updates. Source revision and
dirty markers remain visible. This is an alpha milestone, not a stable-release
or universal compatibility claim. Known Windows audio starvation, clock accuracy,
and unperformed long-duration/thermal gates remain documented and parked. No
engine/cache changes, signing-key creation, release publication or tag. The user
separately authorized committing and pushing this source milestone to `main`.

## Verification — 2026-09-10

- Host suite passed: shared UI state timing/capture/pause/reveal, session-bound
  one-shot picker results, rejected stale/stopped requests, canonical media
  confinement, symlink/traversal/unregistered-file rejection, alpha labels and
  existing CPU/audio/presentation tests.
- Shared-toolbar/diagnostic-only-readiness/isolated-media source contracts passed.
- Repository hygiene, diff whitespace and pinned upstream checks passed.
- Debug and unsigned release builds passed, including release lint. APK manifests
  identify versionCode 2 and the expected alpha names/package IDs.
- Debug APK SHA-256:
  `a09cf03f01b5fac36cd6dd5925a2b50ca267eb7959c7051192d5db399fbb8cf6`
- Unsigned release APK SHA-256:
  `24ba4be355442d637fdb4ca23c5e0afb23a925f47bdaabf4b26d62df8cdaf091`
- Core capability revision remains `eed56136fd9803f3`; no instruction-engine
  change was made. Build provenance is `4cd374c6f1ca+dirty`.

## Initial installation blocker and device findings

The connected SM-T500 had both real machines stopped, with no active marker or
DynRec journal. User state was preserved: `incoming` Normal/Software generation 1
and renamed `win98 gpu` DynRec/GPU generation 37. The disposable core-test entry
point attempted its usual data-preserving APK installation, but Android refused
it with `Requested internal only, but not enough space`. No alpha device probes
ran on that attempt; the installed application then remained `0.1.0-dev-debug`,
versionCode 1.

Read-only storage diagnosis found 408,392 KiB available (about 399 MiB), below
Android's 500 MiB low-storage threshold. Downloads contains roughly 0.995 GiB
of the three recently transferred ISOs, which also appear in the app's imported
media metadata with the expected hashes. SHA-256 reads of all three actual
app-private imported files and Downloads files confirmed exact matches. No user ISO, imported attachment or
guest disk has been deleted. Cleanup requires user approval of exact targets.

The user subsequently freed storage and requested installation. Preflight found
8,057,848 KiB free (7.68 GiB), no active session, child process or attempt journal;
both real profiles retained their generations and selections. The data-preserving
update succeeded. Disposable checks exposed two media-boundary issues: Android
canonicalizes app-storage ancestors, and copied profile IDs need not match their
writable-disk directory names. Validation now accepts platform ancestor aliases
while rejecting symlinked media entries, traversal and unregistered files, and
uses the profile-ID import directory consistently in parent and child. Host and
device regressions cover canonical handoff and the copied-profile layout. A later
test-only provenance assertion was updated to count attachments added by the new
live-import probes rather than assume an empty attachment list.

## Installed and smoke-tested — 2026-09-10

The final `REQUIRE_SURFACE=1 scripts/test-sm-t500-core.sh` run passed: settings,
storage/registration, copied-profile media boundary, overlay state, input bridge,
presented guest frames, pause/resume, Normal media change, restart and clean stop.
It used disposable fixtures only; neither real Windows guest was booted. The
silent fixture is not evidence of audio continuity or real Windows performance.
The final host suite and debug/unsigned-release builds also passed.

Android reports versionCode 2 / `1.0-alpha-debug`; the foreground Machines UI
hierarchy contains `v1.0-alpha-debug · 4cd374c6f1ca+dirty` and both original machine
names. Their execution/display selections and generations remain unchanged, with
no active session or attempt journal. Post-update free space is 8,057,988 KiB
(7.68 GiB). The app was left on the Machines screen, not in a guest session.

Native DynRec media/UI behavior still needs a scoped disposable or separately
authorized guest check; successful storage probes do not certify that interaction.
Raw local evidence remains ignored under `artifacts/alpha-*`.
