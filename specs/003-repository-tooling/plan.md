# Implementation Plan: Repository and Backup Tooling

**Feature**: `003-repository-tooling` | **Date**: 2026-08-13

## Summary

Keep the existing flat script entry points for compatibility, add a shared
device-discovery library, and introduce focused install, backup, verification,
and repository-hygiene commands. Document local-data boundaries and the steps
still required before GitHub publication.

## Constitution Check

| Principle | Result |
|---|---|
| Specification is source of truth | Pass: requirements precede tooling changes |
| Reproducibility | Pass: manifests and SHA-256 cover stored and raw data |
| Legal media boundary | Pass: backups remain ignored and ISO files are excluded |
| Primary hardware, portable architecture | Pass: SM-T500 remains default while ADB discovery is generic |
| Safe guest state | Pass: stopped-app precondition, read-only streaming, staged publication |
| Upstream-friendly open source | Pass: local paths, serials, generated output, and guest data remain untracked |

## Structure

```text
scripts/
├── README.md                    # command catalog and safety boundary
├── lib/android-device.sh        # shared ADB and device discovery
├── backup-device-profile.sh     # stopped profile -> verified Debian backup
├── verify-backup.sh             # offline backup verification
├── install-debug.sh             # data-preserving APK update
├── check-repository.sh          # GitHub hygiene gate
└── existing setup/build/test entry points
docs/
├── development.md               # contributor workflow
├── backup-and-restore.md        # backup format and operations
├── publishing.md                # GitHub release gate
└── ../specs/README.md           # public specification-system entry point and feature index
```

## Design Decisions

- Use Bash for device and backup tools because safe pipeline failure handling
  and arrays are required.
- Resolve ADB from `ADB`, Android SDK environment variables, `PATH`, or the
  documented per-user SDK location, in that order.
- Select the sole connected device automatically; require
  `ROBOWINDOWS_DEVICE_SERIAL` when more than one is attached.
- Store a non-executable `manifest.txt` and parse it as data rather than sourcing
  it as shell code.
- Do not move older scripts in this pass; stable paths are referenced by prior
  specifications and test evidence.
- Publish an orphan `main` root snapshot for the first GitHub push. Preserve the
  pre-publication lineage in the verified Git bundle and a local-only backup ref;
  never push that ref.
- Keep the constitution in its existing Spec Kit-compatible location, but link it from a
  non-hidden specification index and README. The roadmap is a prioritization aid, not a release
  commitment or a substitute for acceptance evidence.

## Rollback

Wrap-up validation (2026-09-10) reproduced exit 141 in the benchmark builder:
`objdump -p | grep -q` closes the pipe early under `pipefail`. Consume producer
output fully in its version/import checks and matching host tests; retain strict
producer failures and all assertions. No benchmark bytes or runtime changes.

All changes are tracked source and documentation. Reverting the Git commit
removes the new tools. Backup creation publishes a new directory and never
alters an existing backup or tablet source. The pre-publication lineage can be
restored from the local backup ref or the verified Git bundle if the public-root
transition must be undone.
