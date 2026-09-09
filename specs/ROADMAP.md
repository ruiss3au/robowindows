# RoboWindows Roadmap

This is the short priority list. Each item gets its own numbered feature
specification, plan, tasks, and validation record when work begins.

## Now

- Keep fixed-20k DynRec as the experimental baseline; fixed 30k and bounded
  auto failed audio or overall quality.
- Implement and host-validate Feature 002's experimental-only balanced 100 ms
  scheduler and schema-3 diagnostics under both Normal and DynRec.
- After its disposable x86 gate, run one new matched fixed-20k pair on the copy.

## Next

- If the new matched pair has zero settled underruns, complete Feature 009's
  three-run medians, then the AoE2/audio, ten-cycle, pause/resume, and 30-minute
  thermal gates on the copy.
- Expose Dynamic only for experimental copies if those correctness,
  performance and recovery gates pass.

## Later

- Optimize a measured DynRec hot path only if schema-3 evidence shows DynRec-only
  long `retro_run()` calls; otherwise continue with shared host/presentation work.
- Complete release-readiness work.

## Never

- Use the only writable guest disk for experiments.
- Bundle proprietary guest software or expose upstream emulator controls.
