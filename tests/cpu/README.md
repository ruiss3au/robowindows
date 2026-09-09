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

Protocol-v4 images split the expanded gate into seven stable suites. The 32
mixed cases use the literal seeds and `MIXED_CASE` rule in
`robowindows_x86_gate.S`; each performs reversible arithmetic, a call/return,
a bounded REP copy, a handled invalid-opcode fault, and self-modifying-code
block re-entry. There is no runtime randomness, and every program remains under
the 64-instruction limit.

SM-T500 before/after evidence is recorded in feature 008's `validation.md`.
A CPU fixture pass does not verify Windows filesystem health or stability.
