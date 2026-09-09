# RoboWindows Specifications and Roadmap

This directory is the public record of RoboWindows product decisions. It is
tracked with the source so GitHub readers can see what is implemented, what is
being validated, and what remains an experiment.

## Start here

- [Constitution](../.specify/memory/constitution.md) — project principles,
  safety boundaries, and release gates.
- [Roadmap](ROADMAP.md) — current priorities, dependencies, and non-promises.
- [Publishing checklist](../docs/publishing.md) — requirements before a public
  release or first public push.

## Feature records

| Feature | Scope | Status |
| --- | --- | --- |
| [001](001-standalone-android-emulator/spec.md) | Standalone Android emulator foundation | Implemented |
| [002](002-realtime-emulation-audio/spec.md) | Realtime emulation and audio | In progress |
| [003](003-repository-tooling/spec.md) | Repository, backup, and publication tooling | Implemented |
| [004](004-session-controls-and-mouse-capture/spec.md) | Session controls and relative mouse capture | Implemented |
| [005](005-guest-shutdown/spec.md) | Guest shutdown and session lifecycle | Implemented |
| [006](006-visible-build-identity/spec.md) | Visible build identity | Implemented |
| [007](007-experimental-machine-copies/spec.md) | Experimental machine copies and fixed-cycle trials | Implemented; device acceptance in progress |
| [008](008-arm64-dynamic-recompilation/spec.md) | Clone-only ARM64 dynamic recompilation | Planned |
| [011](011-gpu-presentation/spec.md) | Experimental GPU presentation | Implemented; Windows smoke pending |

Each feature directory contains its observable requirements (`spec.md`),
implementation and rollback decisions (`plan.md`), executable work (`tasks.md`),
and, where applicable, contracts and validation evidence. A roadmap item does
not change product behavior; it becomes work only when its feature record is
specified and validated.
