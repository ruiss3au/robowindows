# Implementation and rollback

Use project-owned C++17/Java with pinned existing Android tools. Link platform
EGL/OpenGL ES 2.0 only; no new redistributed dependency/license or emulator patch.
This is an independent experimental feature, not a claim that Feature 008 passed.

Add schema-8 presentation preference and Display draft controls. Preserve it in
every profile reconstruction, CPU-mode transition and recovery path. Pass an
explicit allowlisted policy through normal JNI and the isolated service; stable
and recovery launches resolve to Software. No eager metadata migration.

Keep the original software renderer and 66 ms pacing. GPU owns EGL resources on
the presenter thread, uploads XRGB bytes as RGBA with shader swizzle, uses nearest
filtering/clamp-to-edge and handles row padding with byte copies only. Surface
handoff must not destroy GL resources on the UI thread. GPU pacing selects the
latest frame after waiting, has bounded storage and no deadline catch-up bursts.
Destroy/recreate resources on replacement; fall back once per session on GPU
failure. Expose actual/fallback status through normal and isolated status paths.

Extend aggregate telemetry and strict historical-schema-aware report tooling.
Test pure presentation helpers on the host and graphics on disposable device
fixtures. Run hygiene, host tests, pins and Android build before installation;
install only when both real guests are stopped. Tablet must be unlocked and app
foreground for device presentation checks. Record exact evidence and pending
checks; never manufacture a performance pass from static frames or silent audio.

The disposable timer/tone matrix uses a source-owned boot floppy, VGA animation
and PIT channel-2 speaker tone (approximately 440 Hz). BIOS ticks measure guest
time independently of host emulator-call/audio counts. The guest continuously
writes a bounded elapsed-tick record roughly once per guest second on its own
fixture image. A non-exported dedicated process stops/unloads after 122 monotonic
host seconds, then compares the flushed guest tick count with host elapsed time
and deletes only that run's generated files. Startup and record granularity are
included in the 5% tolerance, never subtracted to force a pass. It never opens a
machine disk. The sample confirms
PC-speaker scheduling, not Windows Sound Blaster quality or a human audio judgment.
Run the existing CPU gate first. Use a fresh Surface per session; Android CPU
Canvas ownership must not precede EGL connection on a GPU surface.

Rollback selects Software or disables GPU via a forward fix. No metadata downgrade,
guest disk restore, CPU-engine change or scheduler/audio change. Unknown policies
are rejected at runtime boundaries. Saved GPU preference survives runtime fallback;
the next explicit session start may try it again. No automatic restart or push.
