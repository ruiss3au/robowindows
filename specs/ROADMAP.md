# RoboWindows Roadmap

This is the short priority list. Each item gets its own numbered feature
specification, plan, tasks, and validation record when work begins.

## Now

- Keep fixed-20k DynRec as the experimental baseline; fixed 30k and bounded
  auto failed audio or overall quality.
- Close Feature 008's remaining safety tests and complete the ten-cycle,
  pause/resume, audio and 30-minute thermal gates on the copy.

## Next

- Specify a small redistributable Windows 98 benchmark and repeatable AoE2
  scenario, then compare fixed-20k DynRec with the best sustainable Normal
  profile.
- Expose Dynamic only for experimental copies if those correctness,
  performance and recovery gates pass.

## Later

- Improve video presentation, audio scheduling and host runtime overhead using
  benchmark evidence, then retest rejected cycle policies if justified.
- Complete release-readiness work.

## Never

- Use the only writable guest disk for experiments.
- Bundle proprietary guest software or expose upstream emulator controls.
