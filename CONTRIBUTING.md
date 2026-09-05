# Contributing

RoboWindows is in early hardware-focused development. Issues and focused pull
requests are welcome, but discuss substantial changes in an issue before doing
the work.

Read `AGENTS.md`, `.specify/memory/constitution.md`, and the relevant numbered
specification before changing behavior. Update requirements, plans, tasks, and
device evidence with the implementation. Never submit proprietary guest media,
keys, private identifiers, signing material, generated output, or local paths.

Before opening a pull request, run:

```sh
scripts/check-repository.sh
scripts/test-host.sh
```

For Android changes, also run `scripts/build-android.sh --debug`. Explain what
was tested, on which environment, and whether device validation was performed.
By submitting a contribution, you agree to license it under
GPL-2.0-or-later, the project license.
