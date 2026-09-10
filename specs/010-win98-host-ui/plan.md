# Implementation and rollback

Use existing Java Android Views with shared drawable/widget helpers; no new UI
framework, fonts or dependencies. Gray #C0C0C0, navy #000080,
white inset fields and system typography apply across host screens. Retain
landscape orientation and use scrolling for content that exceeds the viewport.

Extract properties construction and a host-testable draft model. Save all draft
fields through one MachineStore operation using fresh identity/generation and
eligibility checks. Existing schema-7 fields retain Normal settings independently
of selected execution. Generated launch configuration remains derived from those
settings; an interrupted settings publication is regenerated before native entry.
Preserve utility boot media when saving Normal properties; selecting DynRec
requires returning to the Windows disk boot source first. Failed save rolls
back config and preserves an on-disk block marker if rollback cannot complete.

Persist closed-clean state only after the existing shutdown/unload contract.
Clean completion and recovery restore the Normal launch description but retain
the DynRec preference and clean provenance. Other interrupted states retain the
existing Normal fallback/quarantine behavior. No automatic restart.

Run host tests, hygiene, pins and Android build, then install only with both
machines stopped. Use disposable storage/CPU tests before one Normal and one
DynRec UI smoke run on the copy. Guest shutdown needs user cooperation. Capture
only ignored local screenshots and sanitized tracked evidence. Do not resume
AoE2/audio, cycle, soak, clock or median campaigns.

Rollback is a forward fix retaining authoritative profiles and disks. Do not
downgrade metadata, restore disks or push. Keep DynRec experimental; if eligibility
or persistence fails, block that machine instead of guessing a launch path.

Device findings: the legacy direct-core smoke script's unpublished synthetic
profile was rejected by normal machine validation. Restore that entry point only
through a separate debug-only exact fixture-config allowlist, fixed private
directory, non-aliased 1,474,560-byte image and no active/interrupted guest.
Never make the normal machine validator accept arbitrary unpublished profiles.
Recheck build capability in both controller and child before a Windows DynRec
handoff; disposable CPU fixture services retain their separate existing path.
The same legacy script also asserted removed audio/presenter log messages.
Update it to schema-3 presentation counters and stream-open evidence. Its silent
fixture deliberately stays prebuffering; it is not an audio-continuity test.

The first real DynRec UI smoke exposed nearly opaque top and bottom overlays
covering Windows. Apply a shared session-only translucent treatment to Normal
controls and both DynRec bars, including button states. Keep text opaque, preserve
touch targets and guest geometry, and retain opaque classic styling elsewhere.
Add drawable alpha/state regression probes. Build without interrupting the active
guest; install and visually verify only after confirmed clean Windows shutdown.
Rollback affects overlay styling only, never machine state or emulator timing.

Add display-name editing to the existing draft and transactional batch save,
without moving/renaming any disk or directory or changing schema 7. Validate
before publication; keep the name in the dirty-state and rollback tests. Use a
labelled native single-line text field with keyboard focus and no fullscreen
IME editor; switching tabs preserves the unsaved text. Test Apply/OK/Cancel and
dirty-close/error dialogs on stopped machines. Rename only the user's selected
experimental copy to `win98 dynrec exp` after validation, retaining DynRec 20k.

Finish host-UI acceptance using enlarged-text and constrained-layout rendering,
keyboard/touch navigation and dialog checks, preferably with disposable fixtures
and app-local test contexts rather than changing Android-wide display settings.
The completed Normal/DynRec real-copy starts and clean shutdowns remain valid;
use a disposable session for destructive-dialog/input-isolation probes, not a
new Windows campaign. Keep full physical guest-input quality gates explicitly
separate from host-UI acceptance. No push is implied by this follow-up work.

The on-device keyboard/Back check found Android 16 (API 36) bypassed the legacy
`onBackPressed` override and returned to the launcher without resolving a dirty
draft. Register the platform `OnBackInvokedCallback` on API 33+ and share the
existing navigation decision with the legacy callback. Retain IME/dialog Back
handling and test the real system event, not only direct method invocation.
This follows the [Android 16 Back behavior change](https://developer.android.com/about/versions/16/behavior-changes-16#predictive-back).

## Pause milestone and launcher identity — 2026-09-10

Add one original generated raster launcher asset for FR-012, with an adaptive
background/foreground wrapper and legacy fallback. Keep the mark comfortably
inside Android launcher masks and record artwork provenance. This narrow branding
addition is the exception to the original no-raster UI approach; no UI framework
or dependency is needed. Rollback removes only the icon resources/manifest links.
Document outstanding runtime/qualification work as paused by user choice, not
passed. Do not add diagnostic infrastructure or start another guest campaign.
Run host tests, repository checks and the Android build; verify host navigation
and launcher packaging. Install only after verifying both guests clean/stopped,
preserving app data and all profiles. Any physical visual check needs an unlocked
tablet; never boot a real guest to validate a launcher icon.
