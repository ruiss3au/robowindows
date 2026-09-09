# Research: Real-Time Emulation and Clean Audio

## Decision: Treat the deficit as guest-clock starvation, not an audio-format problem

The core declares 48 kHz stereo and emits batches of 800 frames. Measured production was
about 14–18 kframes/s with full surface rendering and 28–32 kframes/s when only every
fourth guest frame was presented. Android opened the requested 48 kHz stream. A larger
buffer can absorb jitter but cannot manufacture the missing guest time.

Frame production is correlated with emulated progress but is not an independent clock: a
core defect could emit nominal frame counts with incorrect guest timing. Acceptance must
pair it with a guest-visible timer or deterministic timer workload.

**Consequences**: optimize the emulator-thread critical path and CPU profile before audio
polish. Do not resample 14–32 kframes/s to 48 kHz as a “fix”; that would conceal slow guest
time or alter duration, pitch, and latency.

## Decision: Decouple video presentation with a latest-frame mailbox

The current `video_refresh` performs `ANativeWindow_lock`, a full output clear, nearest-
neighbor scaling, per-pixel channel conversion, and post while holding the window mutex on
the emulator thread. The measured improvement from dropping three of four presentations
establishes that this path is material.

First benchmark the cost of a full guest-frame copy. The callback will then publish a
copied latest frame into bounded storage, or exchange ownership among bounded slots if the
copy itself is material. A presenter thread
will acquire the newest frame, discard superseded frames, scale/post at a capped cadence,
and handle surface changes. Guest execution never waits for display cadence.

**Alternatives rejected**:

- Permanent modulo-four work inside `video_refresh`: useful diagnostic, but still blocks
  the emulator thread and encodes a guest-refresh assumption.
- An unbounded frame queue: increases latency and memory while showing obsolete frames.
- Immediate graphics-API rewrite: potentially useful later, but a mailbox first proves the
  clock separation with less code and risk.

## Decision: Use a bounded SPSC ring and explicit audio lifecycle

DOSBox's batch callback is the sole producer and AAudio's data callback is the sole
consumer. This is a single-producer/single-consumer problem; the real-time callback must
not take the current mutex, allocate, log repeatedly, or call blocking APIs.

The ring stores interleaved PCM frames with monotonic read/write positions. Overflow drops
according to a documented bounded policy and increments a counter. Underflow writes
silence and increments counters only; reporting occurs outside the callback. Playback
starts after a bounded prebuffer and pause/background/surface/session transitions stop or
pause consumption, clear stale data, then prebuffer on resume.

**Alternatives rejected**:

- A larger mutex-protected queue: hides short jitter but retains priority inversion and
  cannot correct sustained slow production.
- Blocking the producer: directly slows the guest and risks deadlock during teardown.
- Keeping AAudio running through guest pause: guarantees underruns and consumes stale or
  synthetic silence unrelated to guest progress.

## Decision: Calibrate explicit cycles on the conservative core first

The pinned core documents `cycles=fixed N` as a bounded instruction budget and warns that
an excessive value causes sound dropouts and lag; `cycles=max` consumes as much host CPU
as available. The Windows profile currently specifies `cputype=pentium_slow` and no
explicit cycles. Candidate fixed values must therefore be measured rather than guessed.

Select the highest fixed value that sustains wall-clock time, 48 kHz production, user
interaction, and the 30-minute soak. Keep `auto` as a measured candidate, not an assumption.
A lower cycle count is acceptable only when Windows remains functionally usable and timing
criteria pass; results belong to a named target-specific compatibility profile.

## Decision: Quarantine ARM64 dynrec until diagnosed

The pinned source enables `C_DYNREC` and `C_TARGETCPU ARMV8LE` for `__aarch64__`. Direct
`core=dynamic` produced an immediate native SIGSEGV on the SM-T500, while the conservative
path boots. Dynrec is therefore compiled but not validated.

Investigation must begin with a symbolized tombstone and inspect executable cache mapping,
write/execute protection transitions, instruction-cache flushes, generated branch targets,
and block linking. Any fix stays as a minimal pinned-core patch behind an experimental flag.
Promotion requires the same correctness, lifecycle, thermal, and disk-safety suite as the
conservative profile.

**Alternative rejected**: enabling dynamic by default because it is compiled. Compile-time
availability is not runtime compatibility evidence.

## Decision: Make recovery profile-based and non-destructive

Before experimental startup, persist a small pending-start record containing the selected
profile and last-known-safe profile. Mark healthy only after sustained foreground execution.
If the previous process died with the marker pending, default the next start to the safe
profile and report recovery through RoboWindows-owned UI. Do not alter guest media.

Raw disk equality is valid only for a failure proven to occur before guest execution.
Windows normally writes during boot, so write-capable tests retain pre/post hashes for
provenance but assess mountability, filesystem integrity, and expected persistent state on
a clone instead of requiring identical hashes.

## Decision: Use bounded, privacy-safe telemetry

One-second snapshots contain counters and state enumerations, not PCM, pixels, guest text,
paths, product keys, or disk contents. Debug builds may export a finite session report;
release builds keep only health data needed for recovery. Paused/background intervals are
tagged and excluded from performance rates.

## Decision: Add an experimental balanced 100 ms fixed-deadline policy

The first matched fixed-20k benchmark pair produced equivalent CPU throughput
and at least 15 presented FPS, but both cores underrran: 121 callbacks under
DynRec and 19 under Normal. The shared frontend discarded lateness beyond one
guest frame and started playback only after 200 ms. Repeating the same capture
cannot establish a promotable profile.

For experimental machines only, retain fixed guest-frame deadlines through up
to 250 ms of debt and immediately execute at most 20 catch-up calls. Clamp and
report larger or persistent debt. Start playback at 100 ms and adjust only the
host call interval by at most one percent when queue depth exits 75–125 ms,
returning to nominal at 100 ms. This modest hysteresis targets scheduling jitter
without changing PCM, hiding an unsustainable guest clock, or allowing an
unbounded catch-up burst.

Stable machines keep their existing lateness discard and 200 ms prebuffer. The
policy is an allowlisted ID passed through Java/Binder/JNI, so neither media
names nor generated DOSBox configuration become hidden controls.

**Alternatives rejected**:

- Repeating the known-failing three-run comparison before changing scheduling:
  it adds no promotion evidence while settled underruns remain nonzero.
- Increasing the queue alone: it raises latency and cannot expose or recover
  discarded host timing debt.
- Resampling, time stretching, silence insertion outside an actual underrun, or
  fabricated audio: each can conceal incorrect guest time or alter guest sound.
- DynRec engine/cache/linker changes before call-duration evidence: the Normal
  run also underruns, so the current evidence points first to shared scheduling.

## Evidence already established

- Target: Samsung SM-T500 (`arm64-v8a`) connected by ADB.
- Host output: requested and actual rate 48,000 Hz; observed burst 192 frames.
- Guest batches: 800 audio frames.
- Production: roughly 14–18 kframes/s with full presentation; 28–32 kframes/s with
  every-fourth-frame presentation.
- Guest display: approximately 70 Hz; every-fourth presentation is approximately 17.5 fps.
- Windows Sound Blaster resources match the emulator: I/O 0220–022F and 0388–038B, IRQ 7,
  DMA 1 and 5. Driver-resource mismatch is not the present cause.
- Direct dynamic-core selection crashes natively before useful guest execution.
