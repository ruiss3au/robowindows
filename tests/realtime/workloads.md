# RoboWindows performance workloads

## RW98BENCH workload 1

Use `scripts/build-win98-benchmark.sh` to build `RWBENCH.ISO`. Attach that ISO
through RoboWindows `Change Media` only to a running experimental copy, then run
`RWBENCH.EXE` from the Windows CD drive. The display must be at least 640×480.

The source-owned Win32 program runs these phases once, in order:

1. **CPU, 10 guest seconds:** 4,096-operation batches of 32-bit multiply, add,
   rotate and XOR. Report operations per guest millisecond.
2. **Memory, 10 guest seconds:** generate and write a 64 KiB working set, then
   read fixed locations for an integrity value. Report KiB per guest millisecond
   (numerically close to MiB/s).
3. **GDI/audio, 10 guest seconds:** alternate visible 640×400 `PatBlt` fills while
   playing a low-volume, embedded 1.378 kHz 8-bit mono reference tone through
   Windows multimedia. Report fills/s; multiply by 0.256 for megapixels/s.

Every phase reports its actual `GetTickCount` duration, work count, integer rate
and integrity value. The program displays the full record and saves it to
`C:\RWBENCH.TXT`. `scripts/parse-win98-benchmark.sh` must accept the record.

Start a 40-second host capture immediately before launching the program:

```sh
scripts/capture-sm-t500-benchmark.sh --profile dynrec-fixed-20k
```

The capture records emulator-call rate, submitted/published/presented FPS,
coalescing, audio production/consumption/failures, observed decoder and thermal
snapshots. The tester separately records audible cracking/pitch, physical input,
normal Windows shutdown and whether the GDI phase visibly remained responsive.

Compare three valid runs per profile using the median CPU, memory and GDI rates.
Keep guest resolution, Android orientation/foreground state, RoboWindows build,
benchmark hash, sound setting and starting thermal state matched. A throughput
win is not a pass if guest time, audio, FPS, stability, input, disk health or
thermal gates regress.

## AoE2 scenario

Use the same saved single-player scenario, map, population, game speed, starting
camera position and two-minute action sequence. Record scroll/drag response,
animation smoothness, audio breakup and host telemetry. This user-owned workload
is qualitative corroboration only; never include its files or screenshots in the
repository and never substitute it for RW98BENCH metrics.
