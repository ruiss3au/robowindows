# RoboWindows agent instructions

These instructions apply to the entire repository. More specific `AGENTS.md`
files may extend them for a subtree.

## Project intent

RoboWindows is a standalone Android interface for DOS-family software and
compatible Windows guests. The Samsung SM-T500 is the primary acceptance
device. RoboWindows owns every visible host control and screen; never expose or
depend on the DOSBox menu, browser, mapper, branding, or on-screen display.

The repository must remain suitable for public GitHub hosting. Never commit
proprietary operating systems, applications, product keys, guest disks, ISO
files, private device identifiers, signing material, or generated local output.

## Source of truth and workflow

1. Read `.specify/memory/constitution.md` and the relevant numbered directory
   under `specs/` before changing product behavior.
2. Follow specification-driven development: update observable requirements,
   plan and rollback decisions, and executable tasks before implementation.
3. Feed device findings and incidents back into the relevant specification and
   documentation; do not leave important behavior encoded only in code or chat.
4. Keep changes small, independently testable, and traceable to requirements.

The original `DOSBOX_WIN98_ANDROID_PROJECT.md` is historical handoff context.
The constitution and current numbered specifications take precedence when later
evidence contradicts it.

## Guest and device safety

- Never experiment on the only copy of a writable guest disk. Use a verified
  backup or disposable clone.
- Before backing up or copying a live profile, require a guest shutdown from
  inside Windows and a stopped RoboWindows process.
- Backups belong on the Debian host under ignored `backups/`, unless the user
  explicitly chooses another host location. Always identify which machine owns
  a referenced path.
- Never overwrite an existing disk, clone, or backup silently. Stage writes,
  verify size and SHA-256, and publish atomically where practical.
- ADB is sufficient for normal development. Do not require root or SSH unless a
  separately approved diagnostic genuinely needs it.
- Discover the device through `scripts/lib/android-device.sh`; do not hard-code
  an ADB serial or developer-specific SDK path in tracked files.
- Do not boot, stop, restore, or otherwise alter the user's real guest unless
  the current request authorizes it. Diagnostic requests alone are read-only.

## Host tooling

- Do not install Debian packages directly. If a prerequisite is missing, write
  or update a reviewable shell script and ask the user to run it.
- Pin upstream revisions and toolchains. Dependency checkouts belong in ignored
  `third_party/`; generated build state belongs in ignored build directories.
- Prefer existing entry points documented in `scripts/README.md` instead of
  ad-hoc command sequences.
- Preserve stable script paths referenced by specifications and evidence unless
  the same change updates all callers and documentation.

## Implementation constraints

- Start compatibility work with the conservative `core=normal` CPU path.
  Fixed-20k DynRec and GPU are opt-in product options under the Feature 008/011
  availability exception; unfinished stability gates remain unpassed. Automatic
  execution and other dynamic cycle candidates remain diagnostic-only.
- Keep upstream emulator modifications minimal, isolated under `patches/`, and
  suitable for review.
- Maintain the narrow project-owned Android/JNI runtime boundary.
- Use app-private storage and Android document-mediated import for user media.
- Avoid logging registration data, private paths, Bluetooth addresses, device
  serials, or unbounded guest/runtime contents.

## Validation

For documentation or tooling changes, run at least:

```sh
scripts/check-repository.sh
```

For host-testable implementation changes, also run:

```sh
scripts/test-host.sh
```

For Android changes, build through:

```sh
scripts/build-android.sh --debug
```

Run device tests only when relevant and authorized. Use disposable fixtures or
verified clones, state whether the tablet must be unlocked/foregrounded, and
record the exact result in the relevant spec or documentation.

## Git discipline

- Inspect `git status` before editing and preserve unrelated user changes.
- Use Git for coherent milestones; do not rewrite or discard user history.
- Run `git diff --check` and the repository hygiene check before committing.
- Do not push or create a GitHub repository/PR unless the user explicitly asks.
- Before the first public push, complete `docs/publishing.md`, including the
  license, third-party notices, and full-history private-data review.
