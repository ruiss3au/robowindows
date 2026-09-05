# GitHub publication checklist

The source tree is being prepared for public GitHub hosting, but publication and
release are separate gates. Run this before every public push:

```sh
scripts/check-repository.sh
git status --short
```

## Required before the first public push

- [x] Select and add the GPL-2.0-or-later project license, confirmed by the
  maintainer on 2026-09-05.
- [x] Add third-party notices and confirm licenses for DOSBox Pure, reference
  repositories, Gradle wrapper files, and every distributed asset.
- [x] Review the repository history, not only the current tree, for guest media,
  product keys, signing material, device identifiers, and oversized artifacts.
  The review found none; the maintainer chose a history-free public root snapshot
  so pre-publication commits and author metadata will not be published.
- [x] Add contribution, security-reporting, and code-of-conduct documents if
  outside contributions will be accepted.
- [x] Configure a clean GitHub CI build using commit-pinned actions and the
  toolchain versions in `lock/sources.lock`.
- [x] Confirm the README compatibility claims match checked-in SM-T500 evidence
  in `docs/testing.md`, `docs/realtime-baseline.md`, and specification validation
  records.

## First public push

The first public `main` is one audited root snapshot. The private pre-publication
lineage remains only in the ignored verified bundle and a local-only backup ref.
Never push refs whose names begin with `private/`.

## Naming review

A preliminary exact-name web and GitHub search on 2026-09-05 found no obvious
current open-source emulator project using RoboWindows. It did find unrelated
historical uses of the word. This is not a formal trademark clearance. The
README identifies RoboWindows as independent and acknowledges Microsoft's
Windows trademark; no project text claims Microsoft endorsement.

## Never publish

- `backups/`, user disk images, ISO files, installed applications, or keys;
- `artifacts/` screenshots that reveal registration information or private data;
- debug/release keystores or environment files;
- local SDKs, dependency checkouts, Gradle caches, or absolute private paths;
- an APK without matching source, dependency pins, notices, and checksum.

The local repository hygiene script is a fast guard, not a legal or historical
secret scanner. A deliberate history and licensing review remains mandatory.
