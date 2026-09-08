# Requirements Checklist: ARM64 Dynamic Recompilation

- [x] Stable and experimental machine boundaries are explicit.
- [x] Clean-shutdown, crash-loop recovery, and disk-safety behavior are observable.
- [x] Root-cause and minimal-patch requirements precede implementation.
- [x] Correctness, performance, audio, input, shutdown, and thermal gates are measurable.
- [x] Promotion, rejection, rollback, and exclusions are explicit.
- [x] Start-time validation covers every entry point and writable attachment.
- [x] Exact initial cycle policy and return-to-Normal interaction are defined.
- [x] Durable ordering, late crashes, failed persistence and stale callbacks are specified.
- [x] Shared emulator state and normal-core regression risks have explicit gates.
- [x] Media quarantine and a read-only recovery procedure are defined.
- [x] Numerical performance gates distinguish liveness from correctness.

These checks assess specification completeness, not implementation or device acceptance.
