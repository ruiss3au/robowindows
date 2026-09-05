# Development workflow

RoboWindows uses specification-driven development: observable requirements live
under `specs/`, implementation plans record technical decisions, and task lists
track delivery. Incidents and device evidence feed back into those documents.
Repository-wide automation and coding-agent rules are defined in `AGENTS.md`.

## Repository map

```text
android/       product UI, profile storage, JNI boundary, and native runtime
docs/          maintained project and operational documentation
lock/          pinned upstream and toolchain revisions
patches/       minimal auditable upstream patches
scripts/       setup, build, validation, device, and backup commands
specs/         numbered feature specifications, plans, contracts, and tasks
tests/         host Java and C++ tests
artifacts/     ignored local builds and evidence
backups/       ignored Debian-side guest backups
third_party/   ignored pinned dependency checkouts
```

The original project handoff remains in `DOSBOX_WIN98_ANDROID_PROJECT.md` for
historical context. The constitution in `.specify/memory/constitution.md` and
the numbered feature specs are authoritative when old handoff assumptions no
longer match tested behavior.

## Common loop

1. Update or add a numbered specification before changing product behavior.
2. Record design decisions and rollback in its `plan.md`.
3. Implement small independently testable tasks.
4. Run `scripts/test-host.sh` and `scripts/check-repository.sh`.
5. Build with `scripts/build-android.sh --debug` when Android code changes.
6. Run the relevant SM-T500 test without using the only writable guest disk.
7. Update evidence and task status, then commit one coherent change.

See `scripts/README.md` for the command and side-effect catalog.
