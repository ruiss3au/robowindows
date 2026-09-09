# Expanded x86 correctness gate

Status: implemented and device-validated. Feature 008, FR-038, T030–T034.
Catch instruction interactions before another Windows dynrec trial; this is not
an exhaustive x86 compatibility claim.

## Required suites, in order

| Priority | Suite | Required cases |
| --- | --- | --- |
| P0 | Strings | MOVS/STOS/LODS/CMPS/SCAS, byte/word/dword; applicable REP/REPE/REPNE; both directions; zero/one/multiple counts; 16/32-bit addressing and wrapping |
| P0 | Fault/retry | Non-present source/destination and write protection; first/middle/last iteration; aligned and split-page operands; saved instruction/error, partial registers, completed memory and guards |
| P1 | Integer/flags | ADD/ADC/SUB/SBB, logical operations, INC/DEC, shifts/rotates, multiply/divide, sign/zero extension; boundary operands, carry/overflow, divide traps |
| P1 | Stack/control flow | PUSH/POP, near/far CALL/RET and branches, conditional branches/LOOP, interrupt/IRET; size overrides, stack boundaries, cycle-limited resumption and block linking |
| P1 | Paging/SMC | Preserve INVLPG and nested-fault regressions; read-only/user/supervisor mappings, repeated invalidation, same/cross-page code modification and block re-entry |
| P2 | x87/mixed execution | Explicit control word, rounding, stack/status, exceptions and conversions; bounded programs combining calls, strings, faults and code modification |

The pinned ARM64 generic x87 backend intentionally does not synthesize x87
exception-status bits for arithmetic (`FPU_FDIV` documents flags as unimplemented).
The v4 gate therefore checks masked divide-by-zero completion and its exact
positive-infinity result, while comparison condition codes, TOP, control-word
rounding and conversions are checked separately. Exception-status and unmasked
x87 trap delivery remain an explicit coverage gap; they are not represented as
passing cases or used to claim exhaustive x87 compatibility.

Use the configured `pentium_slow` feature set. Unsupported ISA extensions are
explicit negative tests where relevant, not silently skipped required coverage.
Audit uncorrected sibling string helpers first; do not copy the STOSD fix blindly.

## Oracle and protocol

Protocol v4 assigns stable suite identifiers: `0x0101` strings, `0x0102`
fault/retry, `0x0201` integer/flags, `0x0202` stack/control flow, `0x0203`
paging/SMC, `0x0301` x87, and `0x0302` mixed fixed-seed programs. Each suite
uses its own source-built image and fresh isolated process. A result is a
52-byte little-endian record containing an eight-byte magic, protocol version,
record length, suite ID, case count, 64-bit expected and completed case masks,
64-bit expected outcome signature, fixed first-failure assertion ID,
reserved-zero field, completion marker, and 32-bit FNV-1a integrity value over
the preceding bytes. Case counts are 1–64
and the expected mask must have exactly that many low bits set. The parser
rejects nonzero reserved data and trailing bytes; this fixed record is below
the 4 KiB protocol maximum.

The build-specific capability report contains exactly one Normal and one DynRec
record for every required suite plus the preserved v3 regression record. Suite
order on the wire is not trusted. Missing, duplicate, unknown, cross-suite,
cross-mode, stale-version, incomplete, malformed, or nonmatching records fail
closed and cannot update the capability token.

- Run identical source-built inputs in reference QEMU, pinned Normal and ARM64
  DynRec. Record tool versions and image hashes. Normal disagreement is a finding,
  not automatic proof that DynRec is wrong.
- Compare exact memory and architecturally defined registers/flags; mask undefined
  flags. Specify x87 expectations and any justified tolerance per case/control
  word. Ambiguous oracle results block the affected gate pending review.
- Check fault-entry state and final results. Report fixed first-failure case and
  assertion IDs. Keep proprietary binaries and real-guest state out of fixtures.
- Preserve existing regressions. Version the expanded protocol and capability
  token; suite/input/expectation/core changes invalidate earlier passes.
- Bound records to 4 KiB with version, suite/case IDs, completion and integrity
  checks. Reject missing/duplicate suites, malformed lengths, unknown versions,
  incomplete execution and corrupt checksums. Partial suite completion must fail.

## Exploration and acceptance

Add 32 fixed-seed mixed programs of at most 64 instructions, with bounded loops
and fixture-only memory. Commit seeds/generation rules. Minimize failures into
small deterministic regressions before core changes; retain the original seed.

The committed `MIXED_CASE` macro is the generation rule: each listed 32-bit
seed drives the same bounded reversible arithmetic, near call/return, two-dword
REP copy, handled invalid-opcode fault, and immediate-field code modification
followed by block re-entry. The 32 literal seeds in source are the complete
corpus; there is no host random-number dependency. A failure is retained under
its original seed/case ID while its minimized regression receives a separate
deterministic case in the relevant focused suite.

Split suites into fresh disposable images/processes, at most 10 seconds per
image, with a bounded whole-run deadline derived from suite count. Do not extend
timeouts to turn incorrect execution into success. Accept only app-private
fixture cache paths, never machine paths; verify cleanup.

Cold and already-running Activity requests for the debug diagnostic must invoke
the same guarded controller exactly once. A warm request is posted after intent
delivery and consumes its trigger; it must neither silently do nothing nor start
while a guest or another CPU diagnostic is active.

Acceptance: host negative/parser tests pass; all required reference/Normal cases
pass; three consecutive complete SM-T500 Normal/DynRec comparisons match; all
old regressions pass; no queue wipe/reset or incomplete record counts as success.
Document remaining coverage gaps. Windows trials require this complete gate,
experimental disk recovery and separate user authorization. Stable incoming is
never a test target.
