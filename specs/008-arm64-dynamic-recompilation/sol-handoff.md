# GPT Sol handoff

T030–T034 from `expanded-cpu-coverage.md` were completed on 2026-09-09. This
file is retained as historical handoff context; `validation.md` contains the
resulting evidence.

## Current state

- Work is uncommitted, including untracked fixture/services/tests. Preserve it;
  do not reset, commit or push without a request.
- Patches 0002–0005 correct the reproduced INVLPG, precise string-fault and
  supervisor write-protection defects, with bounded before/after evidence.
- Current preserved v3 fixture mask: `0x3fff`; image SHA-256:
  `a439417c22576a86266349b9e8f943448e30a37ef90753fcbcb99354876156c9`.
  Reference QEMU, host tests, Android build and three consecutive complete v4
  tablet comparisons passed. The corrected debug APK is installed.
- User-stopped `incoming - copy` remains Needs disk check; dynrec process exit
  was verified. Stable `incoming` was not started. No Windows boot is authorized
  for coverage work. The IE/MSOBWEB.DLL failure is not proven resolved.

## Remaining product work

The disposable CPU gate is complete but does not promote DynRec to ordinary
machine settings. The experimental copy still needs its normal-core disk-check
recovery, followed by separately authorized Windows correctness, audio and soak
testing. Stable `incoming` is never a test target.

Captures remain local/untracked. Do not publish guest screenshots, registers or
memory.
