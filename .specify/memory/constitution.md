# RoboWindows Constitution

## Core Principles

### I. Specification Is the Source of Truth

Every implementation change MUST trace to a reviewed requirement, acceptance
scenario, or corrective task. Requirements describe observable behavior before
technology choices. When code and specification disagree, the discrepancy MUST
be resolved explicitly; neither artifact may silently drift.

### II. Reproducibility Is Non-Negotiable

Source repositories, toolchains, Android API/NDK versions, build options, and
guest-machine configurations MUST be pinned and recorded. Published artifacts
MUST carry build provenance and SHA-256 checksums. A clean checkout with the
documented toolchain MUST reproduce equivalent APKs without floating `latest`
dependencies.

### III. Legal, User-Owned Media Boundaries

The repository and releases MUST NOT contain proprietary operating-system
files, applications, product keys, private signing keys, or drivers lacking
redistribution permission. Import workflows MUST make the ownership boundary
clear. The project MUST NOT imply that Microsoft endorses it or that every
Windows release is included or universally compatible.

### IV. Primary Hardware, Portable Architecture

The Samsung SM-T500 running Android is the primary acceptance target. Its
actual behavior is authoritative for release gates. Architecture MUST avoid
unnecessary device-specific or root-only dependencies so other compatible
ARM64 Android devices can be supported later. Root access MUST NOT be required
for normal operation.

### V. Native PC Input Semantics

Physical keyboard and mouse input MUST reach the guest as PC input, without a
gamepad abstraction or RetroArch hotkey layer. Down/up transitions, left/right
modifiers, function keys, relative pointer motion, buttons, wheels, capture,
release, and reconnect behavior MUST be independently observable and tested.
Android devices with mixed keyboard/game-controller sources MUST retain their
keyboard behavior.

### VI. Safe Guest State

User media and the only copy of a guest disk MUST never be modified during
development or destructive tests. Imports, test runs, suspend/resume, and exit
paths MUST favor explicit copies, durable flushes, and recoverable failure.
Golden images are immutable test inputs; writable runs use verified clones.

### VII. Correctness Before Optimization

Compatibility MUST first pass on the conservative/interpreter execution path.
Dynamic recompilation, graphics acceleration, and other optimizations are added
one at a time with before/after correctness and performance evidence. A faster
configuration that corrupts guest state or regresses input is unacceptable.

### VIII. Incremental, Independently Testable Delivery

Work MUST proceed through small user-visible milestones. Each compatibility
claim MUST name a concrete guest profile, configuration, test media provenance,
and repeatable result. “DOS and Windows support” is a roadmap, not an unbounded
promise. Tests SHOULD fail before behavior is implemented when automation is
practical.

### IX. Upstream-Friendly Open Source

Changes to upstream emulator or platform code MUST be minimal, clearly isolated,
and suitable for upstream review where practical. New code MUST use licenses
compatible with GitHub publication and all third-party notices MUST be retained.
Generated outputs and local paths MUST stay out of version control.

### X. Product-Owned Interface

RoboWindows MUST own every visible host screen, control, error, notification,
and overlay. The emulator is presented only as guest video and audio behind a
narrow runtime boundary. Upstream menus, browsers, mappers, settings, branding,
and on-screen displays MUST NOT be exposed, re-skinned, or relied upon as hidden
product behavior.

## Required Quality Gates

1. Specification gate: prioritized scenarios, explicit exclusions, measurable
   outcomes, and no unresolved high-impact ambiguity.
2. Planning gate: license review, dependency pins, target hardware facts,
   architecture rationale, and rollback strategy are documented.
3. Desktop/reference gate: freely redistributable test media passes the common
   emulator and input-independent checks where a reference build is applicable.
4. SM-T500 gate: the same test inputs pass on the connected tablet, first on the
   conservative core and then on any optimized core advertised as supported.
5. Release gate: source build is reproducible, checksums and notices exist, no
   prohibited assets are present, and advertised compatibility matches evidence.

## Governance

This constitution governs specifications, plans, tasks, implementation, and
releases. Any exception MUST be documented in the relevant plan with the reason,
the rejected simpler option, the risk, and an exit strategy. Amendments require
a version change and a note describing affected specifications and migrations.

**Version**: 1.1.0 | **Ratified**: 2026-08-09 | **Last Amended**: 2026-08-09
