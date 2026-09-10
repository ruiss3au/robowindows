# Validation — 2026-09-09

## Opt-in modes and optional diagnostics — 2026-09-10

T014/T015 scoped acceptance passed: stopped ordinary-machine options, separate
CPU action/status, two full passes and cancelled-rerun invalidation, remembered
counter checkbox/session toggle, translucent control layout, host checks and
both build variants. Existing profile selections remained unchanged. See
[exact scope and artifacts](../../docs/opt-in-promotion.md); no new Windows
DynRec visual session or long campaign was run.

## Launcher and pause wrap-up — 2026-09-10

FR-012/T012 adds original generated retro-computer artwork with legacy and
adaptive launcher resources, both manifest icon attributes and no new permission
or dependency. [Artwork provenance and prompt](../../docs/app-icon.md) are tracked;
the PNG is the intentional product asset, not a captured guest screenshot.
The built-in image-generation skill supplied the artwork. Runtime/native sources,
CPU/cache policy, audio scheduling and presentation behavior are unchanged.

Android debug build passed; APK SHA-256 is
`e50ae096019128e8b80bf56c3f6de8fa57fbf2b57b6ca7528da92870df41e432`,
source label `91dda65f6498+dirty`. Installed APK readback matched exactly.
The complete host suite passed after repairing a reproduced pre-existing SIGPIPE
false failure in benchmark tooling (Feature 003 T015). Repository hygiene,
whitespace and pinned-source checks passed. No extra device CPU, benchmark,
pressure fixture or Windows performance campaign was run for a resource change.

Installation used the existing data-preserving debug installer only after
verifying clean profiles, no active-session marker, isolated runner or recovery
journal. The unlocked/awake SM-T500 displayed the new mark without clipping in
its circular app-drawer mask; tapping it opened the host library, not a guest.
Machines and the stopped copy's General/CPU/Display properties rendered; Normal
20k and GPU were visibly selected, with a clean draft's Apply disabled. Properties
were closed with Cancel, without selecting or saving changes. About was inspected
and closed with Android Back. Tests displayed the retained CPU capability pass
without running a fixture; Back returned to Machines. This is a host-navigation/icon smoke, not renewed
guest compatibility, audio quality or thermal acceptance.

Postflight verified stable `incoming` Normal/Software generation 1 and the copy
`win98 dynrec exp` Normal/GPU fixed-20k generation 32, both clean/stopped and
unchanged from this milestone's preflight. The user had selected Normal before
this work; the previous generation-31 DynRec evidence is historical. No guest
disk was opened and no guest was booted or stopped by the agent. Ignored local
evidence is `artifacts/wrapup-*` (host logs, UI dumps and screenshots).

T013 records the user's decision to pause in the roadmap and
[practical-use handoff](../../docs/current-handoff.md). Unfinished audio/cache
attribution, guest clock/medians, experimental promotion and dropped manual/thermal
gates remain explicitly incomplete. No further investigation is automatically
queued. Local commits only; no push or release qualification is implied.

## Original UI implementation evidence

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

Before installing that correction, the user confirmed shutting Windows down.
Postflight on the DynRec process verified BIOS APM power-down, guest-requested
shutdown and clean stop. There was no active-session preference, attempt journal
or isolated child remaining. The library retained `DynRec (experimental) · 20k`
and `Start DynRec` for the stopped copy, confirming real clean-shutdown selection
persistence. Stable `incoming` still showed Normal and stopped. This supersedes
the pending shutdown/persistence observations above, but does not establish
unreported keyboard/mouse readiness or complete T007.

The correction APK was then installed through
`REQUIRE_SURFACE=1 scripts/test-sm-t500-core.sh` after its stopped-state preflight.
The script passed, including the new session-overlay alpha/state pixel probes,
unchanged opaque non-session styling, storage/input probes, disposable Normal
surface presentation and clean unload. The real guest disks were not used by
this test. This supersedes the pending installation/probe observation above;
the actual guest-overlay visual check remains separate.

The stopped copy was reopened through its retained `Start DynRec` action. The
initial inspected telemetry identified configured/current DynRec; ignored local
screenshots then verified both bars over the Windows desktop: top-row desktop
icons and the bottom Windows taskbar/Start button remain visible through the
panels, and taskbar content remains visible through the readiness buttons.
Stop/readiness controls and guest geometry are retained. T008 is complete;
the user still needs to confirm usability and shut down this visual-check
session. This is not a new performance or audio acceptance result.

The user subsequently confirmed the overlays were "much better" and reported
shutting Windows down. Final postflight verified BIOS APM power-down,
guest-requested shutdown and clean stop for this visual-check session, no
active-session preference or attempt journal, and no isolated child process.
Both library rows are stopped; the copy still selects experimental DynRec 20k
and stable `incoming` remains Normal. The overlay correction is accepted and
requires no further guest trial. Broader T007 enlarged-text/navigation/dialog
and explicitly confirmed input checks remain unclaimed; no omitted quality gate
has been promoted to passed.

## Name editing and final UI acceptance — 2026-09-10

General properties now edit the display name through the existing staged batch
save. Host tests cover normalized whitespace, empty/control/format rejection,
Unicode names and the 64-code-point boundary. Disposable Android checks cover
rename publication, invalid-name rejection, stale-generation rejection, active
save rejection, publication rollback preserving the old name, unchanged machine
identity/media paths and retained clean provenance/CPU choice.

Android view probes passed draft retention across tabs, Cancel, Apply, OK and
normalized saved text. All four properties tabs passed measured touch-target,
button-text-height and scroll-viewport checks at 640×360 dp with 100% and 150%
font scale, and 1000×600 dp with 200% text. These used app-local resource contexts;
the tablet's system display/font settings were not changed. Title bars ellipsize
long names instead of consuming the properties viewport.

The real keyboard/Back check found an Android 16/API-36 regression: system Back
bypassed the legacy callback and left a dirty property sheet without prompting.
No guest was running and stored preferences stayed unchanged. After registering
the platform callback, system Back showed `Discard changes?`; Tab focused
`Keep editing`, and Enter returned to the intact draft. Cancel preserved the
entire preferences checksum. Empty-name Apply showed `Settings not saved` /
`Enter a machine name.` rather than publishing anything. Tests and About both
returned to Machines through the actual system Back event.

Apply then renamed only the stopped experimental copy from `incoming - copy`
to the user-selected `win98 dynrec exp`. Its generation advanced exactly once;
redacted comparisons verified every other profile field unchanged, identical
stable-profile metadata, and identical non-profile preferences (including clean
shutdown provenance). The library showed the new name and retained `Start
DynRec` / experimental 20k. Stable `incoming` was not renamed or started.

The complete host suite, repository hygiene, diff whitespace, pinned sources,
Android build and `REQUIRE_SURFACE=1 scripts/test-sm-t500-core.sh` passed. Final
installed candidate SHA-256:
`0029dd9276059d6acced548cc9be082670f5e6f4cce0311115c9e1343d98f350`.
The disposable script included successful overlay, settings, enlarged-layout,
persistence/input bridge and core lifecycle probes. Real Windows sessions were
not needed for this follow-up; their earlier starts/clean shutdowns and overlay
acceptance remain recorded above. Session-dialog checks are being completed on
the disposable fixture, not on either user disk.

The disposable blank-disk session verified actual system Back revealing controls
then showing `Stop without guest shutdown?`. Tab/Enter selected Cancel and kept
the same session running. Restart showed its unsaved-work warning; system Back
dismissed it without a restart (the bounded log contained one start and no
restart). Explicit Stop then cleanly unloaded only this fixture and returned to
Machines. The three generated fixture files were removed. Final postflight
found no active-session preference, attempt journal or isolated child, and both
real machines stopped with the renamed copy still selecting DynRec 20k.

T007 and T009–T011 are complete for the scoped host UI: real-copy Normal/DynRec
start/shutdown evidence above, automated storage/layout probes, and on-device
touch/keyboard/Back/dialog checks. Keyboard events here are ADB-injected Android
UI events, not a new physical guest-keyboard/mouse certification. This closes
Feature 010 without claiming the omitted audio, timing, thermal, repeated-cycle
or full guest-input quality gates. No further Windows trial was needed.
