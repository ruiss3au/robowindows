# Implementation and rollback

Use existing Java Android Views with shared drawable/widget helpers; no new UI
framework, fonts, raster assets or dependencies. Gray #C0C0C0, navy #000080,
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
