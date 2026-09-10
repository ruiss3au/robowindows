# RoboWindows performance workloads

## RW98BENCH workload 2

Use `scripts/build-win98-benchmark.sh` to build `RWBENCH.ISO`. Attach that ISO
through RoboWindows `Change Media` only to a running experimental copy, then run
`RWBENCH.EXE` from the Windows CD drive. The display must be at least 640×480.

The source-owned Win32 program runs these phases once, in order:

1. **CPU, 10 guest seconds:** 4,096-operation batches of 32-bit multiply, add,
   rotate and XOR. Report operations per guest millisecond.
2. **Memory, 10 guest seconds:** generate and write a 64 KiB working set, then
   read fixed locations for an integrity value. Report KiB per guest millisecond
   (numerically close to MiB/s).
3. **GDI, 10 guest seconds:** draw pseudorandom 64×64 `PatBlt` rectangles into a
   320×200 compatible bitmap. Report rectangles/s and multiply by 0.004096 for
   megapixels/s. Randomized work is never copied to the display. A narrow progress
   strip advances at no more than ten guest updates/s and its update FPS is
   reported separately.

The compact utility shows live elapsed/work/rate values about four times per
guest second, plays no sound, and handles Close or Escape during every phase.
Every phase reports its actual `GetTickCount` duration, work count, integer rate
and integrity value. The program displays the full record and saves it to
`C:\RWBENCH.TXT`. `scripts/parse-win98-benchmark.sh` must accept the record.

Start a matched host capture immediately before launching the program:

```sh
scripts/capture-sm-t500-benchmark.sh --profile dynrec-fixed-20k
```

The capture records emulator-call rate, submitted/published/presented FPS,
coalescing, audio production/consumption/failures, observed decoder and thermal
snapshots. The tester separately records audible cracking/pitch, physical input,
normal Windows shutdown and whether the utility remained responsive.

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

## Disposable stress/tone workload 1

Build with `bash scripts/build-presentation-fixture.sh OUTPUT stress`; validate
the production image in QEMU with `bash scripts/test-stress-reference.sh` (also
part of `scripts/test-host.sh`). GNU binutils 2.40 and QEMU 7.2.22 remain pinned.
Run on the stopped/unlocked tablet with:

```sh
bash scripts/test-sm-t500-presentation.sh normal 1 stress
bash scripts/test-sm-t500-presentation.sh dynamic 1 stress
```

Run sequentially, Normal first. These commands use only packaged source-owned
media, fixed-20k and balanced 100 ms; presentation `1` means GPU (`0` is Software).
The existing default `tone` fixture is byte-for-byte unchanged. The stress
variant repeats four phases, each 144 BIOS ticks (about 7.91 guest seconds):

1. Idle using HLT until the next interrupt.
2. Integer multiply/add/rotate/XOR batches with 32-bit operands.
3. Fill and copy separate 16 KiB RAM ranges at 0x20000 and 0x30000.
4. Repeated 64,000-byte VGA writes at 0xA0000.

Interrupts stay enabled. BIOS ticks continue independently of host call counts;
the PIT speaker tone remains active and guest PCM is not modified. Code runs in
real mode, not Windows protected mode; it is not an AoE2, driver or game-logic
reproduction. The user clarified that the earlier Windows captures included
opening windows and then AoE2, motivating heavier synthetic phases without using
any game files. Precise phase correspondence to those captures is unknown.

Only sector 18 of the disposable floppy is written. Its little-endian record is
magic `RWSTR001` (8 bytes), elapsed BIOS ticks (uint32), running status 1 (uint16),
visited-phase mask 15 (uint16), then four positive completed-work batch counters
(uint32 each). The record is flushed on core unload after 122 host seconds;
both identity and all-phase evidence are required before the guest-clock check.
Quality still requires zero settled audio/timing/graphics errors and guest-clock
error within 5%; a stress failure is evidence, never a reason to weaken the gate.
Phase scheduling is guest-clock based; do not align it exactly to host timestamps
if guest time has slowed. This workload establishes no Windows/game acceptance.
