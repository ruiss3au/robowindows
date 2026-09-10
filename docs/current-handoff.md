# Practical-use handoff — 2026-09-10

Development and performance investigation are **paused by user choice** after
the launcher-icon wrap-up. The app remains usable; unfinished quality work is
not a requirement to keep experimenting before using it. Do not automatically
resume diagnostics, fixture development, benchmarks or long acceptance campaigns.

## Use the app

- Open RoboWindows from its retro-computer launcher icon. Machines, Tests and
  About remain separate; selecting a machine does not start it.
- Use **Settings** on a stopped machine. **General** edits its display name;
  **CPU** selects Normal or eligible experimental DynRec; **Display** selects
  Software or eligible experimental GPU. **Apply/OK** saves, **Cancel** discards.
- For the existing experimental copy, Normal + GPU is a practical combination
  to try when desired. GPU targets about 30 presented FPS (Software about 15);
  it accelerates host presentation, not guest CPU or 3D/game rendering. CPU and
  presentation choices are independent. No settings were automatically changed
  during this wrap-up; stopped-state inspection found the copy already selected
  Normal/GPU (fixed 20k, generation 32), and that selection is preserved.
- Keep the stable machine as the conservative Normal/Software fallback. GPU
  and DynRec remain restricted to eligible experimental copies; do not promote
  or alter stable `incoming` to make either selectable.
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
| GPU and DynRec promotion | Both stay experimental. GPU presented FPS does not establish unique game FPS or solve guest rendering lag; sustained GPU/thermal behavior and Feature 008 qualification remain incomplete. |
| Release readiness | A debug development APK is not a qualified public release. Recheck publishing/privacy, licensing, reproducibility and advertised compatibility before a release or newly authorized push. |

Evidence: [Windows cache capture](../specs/002-realtime-emulation-audio/translation-attribution.md#windows-cache-pressure-capture--2026-09-10),
[GPU validation](../specs/011-gpu-presentation/validation.md),
[UI acceptance](../specs/010-win98-host-ui/validation.md), and
[short roadmap](../specs/ROADMAP.md). Resume later by checking repository/device
state and this handoff first; do not assume a guest is stopped or start one
automatically. Local commits are permitted; pushing needs a new explicit request.
