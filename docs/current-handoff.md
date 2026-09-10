# Practical-use handoff — 2026-09-10

Performance investigation remains **paused by user choice**. The subsequent
opt-in promotion makes DynRec fixed-20k and GPU available on ordinary machines
and in release builds without claiming unfinished quality gates passed. No
existing machine is switched automatically. Do not resume benchmarks or long
acceptance campaigns automatically.

## Use the app

- Open RoboWindows from its retro-computer launcher icon. Machines, Tests and
  About remain separate; selecting a machine does not start it.
- Use **Settings** on a stopped machine. **General** edits its display name;
  **CPU** selects Normal or eligible DynRec; **Display** selects Software or GPU.
  **Apply/OK** saves, **Cancel** discards. New machines still default to Normal
  and Software. See [promotion verification](opt-in-promotion.md). DynRec requires
  a clean prior shutdown and a CPU correctness pass for the installed core build.
- **Tests → Run CPU tests / Run again** runs disposable Normal/DynRec fixtures,
  never Windows disks. Rerunning clears the previous pass until the new run
  succeeds. Cancelled/failed/incomplete runs do not authorize DynRec. Tests are
  unavailable while a session or disk-check recovery is pending.
- **Show performance counters** in Tests, or **Counters: on/off** during a session,
  remembers one app-wide choice, initially off. It controls the existing DynRec
  live decoder display; safety monitoring and fallback notices remain active.
- For the existing experimental copy, Normal + GPU is a practical combination
  to try when desired. GPU targets about 30 presented FPS (Software about 15);
  it accelerates host presentation, not guest CPU or 3D/game rendering. CPU and
  presentation choices are independent. No settings were automatically changed
  during this wrap-up; stopped-state inspection found the copy already selected
  Normal/GPU (fixed 20k, generation 32), and that selection is preserved.
- Keep a conservative Normal/Software fallback and back up important guest media
  before changing modes. Ordinary machines can now opt in through Settings;
  stable `incoming` has not been switched or used for acceptance testing.
- Start the chosen machine explicitly. Keep the tablet unlocked and the app
  foregrounded during use. Shut Windows down from inside Windows when finished;
  host Stop/Exit is not a substitute for orderly guest shutdown.
- Back up app-private guest media before uninstalling or clearing app data.
  See [backup instructions](backup-and-restore.md). Updating with the existing
  debug installer preserves app data; never uninstall to apply an icon update.

## What is good, and what is not established

Classic host UI/settings acceptance is complete, including editable names,
draft saves, modern Back handling and translucent guest controls. Disposable
Normal/GPU and DynRec/GPU stress checks held roughly 29.77 FPS, guest timing
98.82% of host elapsed, and zero captured audio/scheduling/graphics errors.
The user reported excellent sound during an earlier AoE2 session. These are
useful results, not universal Windows compatibility or sustained-quality claims.

Real Windows captures still recorded underruns under Normal and DynRec. The
latest selected DynRec desktop window had 121 underruns, 17,565 missing frames
and one deadline reset at 29.81 FPS. Specific activity was unconfirmed. Cache
pressure/recycling correlates with the overload, but phase costs and causality
remain unresolved. The complete session also sampled PageFault residency.
Do not claim that audio starvation or all page-fault behavior is cured.

## Unfinished work — parked, not passed

| Area | Remaining work / resume condition |
| --- | --- |
| Shared Windows audio and DynRec stalls | Feature 002 T062/T076 remain open. Existing evidence is sufficient to park diagnosis. Only revisit for a noticeable user problem or an explicit optimization request; no new instrumentation or pressure fixture is currently queued. |
| Cache-pressure hypothesis | Retain the T083 source review as a possible starting point, not an implementation plan in flight. No cache-size, replacement, linker or publication-barrier changes are approved by this handoff. |
| Benchmarks | Feature 009 independent guest-clock validation and three-run medians remain incomplete. A good short pair is not a median or clock-accuracy result. |
| Long/manual gates | Further AoE2/audio, pause/resume, ten shutdown cycles and 30-minute thermal runs were dropped from this campaign. Mark them untested, not passed; do not requeue automatically. |
| GPU and DynRec qualification | Both are opt-in product options under an explicit availability exception. GPU presented FPS does not establish unique game FPS or solve guest rendering lag; sustained GPU/thermal behavior and Feature 008 qualification remain incomplete. |
| Release readiness | A debug development APK is not a qualified public release. Recheck publishing/privacy, licensing, reproducibility and advertised compatibility before a release or newly authorized push. |

Evidence: [Windows cache capture](../specs/002-realtime-emulation-audio/translation-attribution.md#windows-cache-pressure-capture--2026-09-10),
[GPU validation](../specs/011-gpu-presentation/validation.md),
[UI acceptance](../specs/010-win98-host-ui/validation.md), and
[short roadmap](../specs/ROADMAP.md). Resume later by checking repository/device
state and this handoff first; do not assume a guest is stopped or start one
automatically. Local commits are permitted; pushing needs a new explicit request.
