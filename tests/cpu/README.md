# Source-owned CPU fixture

The boot loader loads eight sectors at `0x8000`; the stage writes a 22-byte
checksummed result to sector 18 of its disposable floppy. No user media is used.
`scripts/build-cpu-fixture.sh` checks GNU binutils 2.40 and source-derived hashes.
The expanded v3 success mask is `0x3fff`; old `0x1fff` evidence cannot pass.

Result offset 14 is a fixed assertion identifier (zero on success):

| ID | Failed check |
| --- | --- |
| 1 | Retried nested load returned the wrong value |
| 2 | Nested handler entry count |
| 3 | Nested handler return count |
| 4 | Nested handler rejected its fault state |
| 11 | INVLPG absolute disp32: register/flags preservation |
| 12 | INVLPG base plus disp8: register/flags preservation |
| 13 | INVLPG SIB plus disp32: register/flags preservation |
| 14 | INVLPG address-size-16 absolute: register/flags preservation |
| 20 | REP STOSD page-fault count or write error code |
| 21 | REP STOSD saved instruction address |
| 22 | REP STOSD partial count |
| 23 | REP STOSD partial destination |
| 24 | REP STOSD value/source register preservation |
| 25 | REP STOSD completed-prefix memory |
| 26 | REP STOSD faulting destination remains untouched |
| 28 | REP STOSD retry count, destination, or fault count |
| 29 | REP STOSD final destination memory |
| 30 | REP STOSD boundary guards |

The INVLPG regression deliberately uses address bytes `40`: these encode
`INC EAX` if decoded as instructions. The uncorrected dynrec Group 7 decoder
consumes only `0f 01 3d` from `invlpg [0x40404040]`, then decodes four increments.
The normal decoder consumes the address through its effective-address helper.
Patch `0002-dynrec-consume-invlpg-address.patch` makes dynrec do the same,
without reading operand memory or changing the existing TLB-clear policy.

Patch `0003-dynrec-stosd-precise-page-fault.patch` limits its change to the
32-bit store-string helpers. It uses checked writes, reports the fault only
after publishing completed REP progress, and leaves sibling string helpers for
the expanded-suite audit rather than assuming the same correction applies.

Protocol-v4 images split the expanded gate into eight stable suites. The
page-fault progress suite adds a deliberately long handler, sixteen sequential
handlers, and a four-level nested chain with bounded work at every level. Its
three result bits assert only exact guest entry/return order, completion, and
retry behavior; device-dependent PageFaultCore call and duration aggregates are
reported separately and are never encoded as guest expectations. The 32
mixed cases use the literal seeds and `MIXED_CASE` rule in
`robowindows_x86_gate.S`; each performs reversible arithmetic, a call/return,
a bounded REP copy, a handled invalid-opcode fault, and self-modifying-code
block re-entry. There is no runtime randomness, and every program remains under
the 64-instruction limit.

SM-T500 before/after evidence is recorded in feature 008's `validation.md`.
A CPU fixture pass does not verify Windows filesystem health or stability.

The separate `cache_workload.S` performance control is not part of the x86
correctness gate. It uses five independently identified images and a new 64-byte
record, with exact return/checksum assertions and independent PIT ticks. See
[protocol, controls and timing limits](../../specs/002-realtime-emulation-audio/cache-control-fixture.md).
Build with `bash scripts/build-cache-fixture.sh CASE [OUTPUT]`; validate all cases
and an injected failure with `bash scripts/test-cache-reference.sh`. Builders
refuse to overwrite differing existing outputs. QEMU results are correctness
references, not Normal/DynRec performance measurements.
