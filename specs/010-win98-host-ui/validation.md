# Validation — 2026-09-09

The complete host suite (including eight-suite QEMU reference parity), repository
hygiene, diff whitespace, pinned-source verification and Android debug build
passed. New host checks cover draft dirty/reverted/applied baselines, eligibility
reasons and rejection of invalid disposable fixture paths.

Installed candidate SHA-256:
`2e3681215cbccee3b58a53d4e73dedd283f8887dbdc2f58497bb3ba251076920`.
Installation followed verification of no active-session preference, dynamic
journal or isolated child. Neither real Windows disk was opened during disposable
tests. Stable `incoming` was not altered.

Disposable Android storage tests passed: one-generation batch Apply without
start, stale-draft rejection, failed launch/profile publication rollback, stable
and missing-capability rejection, active-session rejection, recovery of each
interrupted clean-completion boundary, idempotent closed-clean replay, persistent
DynRec choice with retained Normal settings, explicit two-copy selection and
unchanged stable-fixture metadata/configuration.

The first legacy direct-core smoke attempt exposed a pre-existing mismatch: its
unregistered synthetic profile was rejected by ordinary machine validation. A
separate exact-config, fixed-directory, non-aliased debug fixture entry point
restored that test without allowing arbitrary unpublished machine launches.
Its next run exposed assertions for removed audio/presentation messages. The
script now checks current schema-3 presented-frame counters and stream-open
evidence, with stopped-state preflight before installation. The final
`REQUIRE_SURFACE=1 scripts/test-sm-t500-core.sh` passed persistence/input probes,
Normal startup, 640×400 publication, surface presentation, pause/resume, media
change, restart and clean unload. It deliberately uses `nosound=true`; an open
stream in prebuffering is not an audio continuity pass.

The complete dedicated Normal/DynRec x86 gate passed on both the initial UI
candidate and the installed candidate identified above. Postflight found only
the main app process, no active-session preference and no attempt journal. The
core and fixture capability hash inputs are unchanged by the UI work.

Initial on-device properties inspection shows the navy title bar, four tabs,
checked Normal and saved-20k controls, available fixed-20k DynRec selection and
disabled Apply on a clean draft. Screenshots remain ignored local artifacts.
On the installed candidate, selecting DynRec displayed an unsaved draft and
left the stored preferences byte-identical; Cancel also preserved them exactly.
The library and CPU properties were inspected on the SM-T500. Final
enlarged-text/navigation/dialog checks and the one-per-core Windows smoke
checks remain pending; T007 is not complete. These are UI checks, not a return
to benchmark, clock, AoE2/audio, repeated lifecycle or thermal campaigns.

The final media-properties refresh correction built successfully and was
installed only while stopped, with APK SHA-256
`5f8655c1986d2559e1e2b833d9b64810c73de0ae85416da975f0c6d3987ab5c6`.
The complete disposable direct-core/storage/input smoke script passed again on
this APK. The dedicated CPU capability remains current: its core/fixture hash
is identical to the candidate whose full gate passed above. No performance or
extended acceptance claim is inferred from these UI implementation checks.

## Real-copy UI smoke — 2026-09-10

On the final APK above, library Start launched `incoming - copy` in Normal at
20k. Startup telemetry identified Normal residency and the balanced-100-ms
policy; the three inspected startup intervals reported no underruns, missing
frames, deadline resynchronizations or lifecycle errors. This limited sample
does not establish whole-session audio or performance acceptance.

The user reported completion of the requested desktop/window check and guest
shutdown. Postflight verified BIOS APM power-down, `guest requested shutdown`
and `guest stopped cleanly`, with no active-session preference, dynamic attempt
journal or isolated child process. The library showed the copy stopped in
Normal. Stable `incoming` remained stopped and was not used. The DynRec smoke
and remaining enlarged-text/navigation/dialog checks are still pending.

The CPU properties then selected fixed-20k DynRec. Apply reported settings saved
while postflight still found no active session, attempt journal or isolated
child; returning with OK showed the stopped copy's `Start DynRec` action and
retained Normal selection on stable `incoming`. Starting that action launched
the isolated guest with both configured and current decoder reported as DynRec.
Of four inspected startup intervals, one reported 25 underruns and 4,176 missing
audio frames, with scheduler lateness reaching 178,014 microseconds; the other
three had zero underruns/missing frames. All four had zero resynchronizations,
stream errors and presentation failures. This startup observation is not an
audio-quality pass or evidence of the cause. No scheduler/engine change or
extended benchmark campaign is authorized by this UI smoke. User input checks,
clean DynRec shutdown and retained selection remain to be verified.

## Overlay correction — 2026-09-10

During that DynRec smoke, the user reported opaque top/bottom bars obscuring
Windows. Inspection found the classic restyle used alpha 250/255 panels,
opaque bevel buttons, and an opaque replacement for confirmed readiness.
FR-009 now keeps session panels and button fills translucent without fading
labels, resizing the guest or removing persistent diagnostic controls. Normal
uses the same session-only treatment; non-session classic panels stay opaque.

The correction passed the complete host suite, pinned-source verification,
Android debug build, repository hygiene and diff whitespace checks. Drawable
pixel/state and non-session opacity probes were added to the disposable Android
self-test but have not yet run on-device. Installation and visual verification
are pending a clean guest shutdown; no running guest was stopped or reinstalled
over. T007/T008 remain open, including clean DynRec selection persistence.
Built correction APK SHA-256:
`7064e74d883d45451a94c5cd1dc94d7e26ece361e1827a3fd89e0e7659ce3ff6`.
