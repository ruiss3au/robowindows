# RoboWindows Roadmap

This is the short priority list. Each item gets its own numbered feature
specification, plan, tasks, and validation record when work begins.

## Now

- Finish fixed-cycle evidence on the experimental Windows 98/AoE2 machine.
- Keep the stable machine unchanged and available as the fallback.

## Next

- Implement [Feature 008](008-arm64-dynamic-recompilation/spec.md): investigate
  ARM64 dynamic recompilation on an experimental copy, with crash recovery and
  correctness gates first.

## Later

- Add a small Windows 98 benchmark and repeatable timer-and-tone workloads.
- Continue performance work on video presentation, audio scheduling, and host
  runtime overhead as evidence directs.
- Complete GitHub publication and release-readiness work.

## Never

- Use the only writable guest disk for experiments.
- Bundle proprietary guest software or expose upstream emulator controls.
