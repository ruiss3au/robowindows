# SM-T500 real-time emulation baseline

**Feature**: `002-realtime-emulation-audio`  
**Captured**: 2026-08-10  
**Status**: Protected host and device baselines complete

## Reproducible inputs

| Item | Value |
|---|---|
| Debug APK SHA-256 | `c0c0572fc80d7e61739ddd3f6392bbc0e68fa77769fd9d2e323f28da89c5362e` |
| DOSBox Pure revision | `7f6e8fb7385fa446d1444d671063268520bf9b54` |
| Android ABI | `arm64-v8a` |
| Target | Samsung SM-T500 |
| Current Android build | Android 12; fingerprint SHA-256 `2e8ad80fd9078d620961b4aaa313b8cec121bcef57a4f4efd0d48e4e34243b57` |
| Safe CPU core | `auto` (direct `dynamic` remains rejected/experimental) |
| Windows cputype | `pentium_slow` |
| SB16 resources | I/O 220 and 388, IRQ 7, DMA 1, high DMA 5 |
| Backup compressed disk SHA-256 | `c417e58ed82cc498f280422ceaca9e08b6dfbf734783d1801b1e91e9ce8de7b0` |
| Backup raw disk size | 2,096,898,048 bytes |

The build fingerprint is represented only by a SHA-256 digest in test evidence so public
documentation does not publish a stable device identifier. This differs from the earlier
LineageOS development baseline in `docs/environment.md`; acceptance evidence must use this
current build identity and must not combine results across the two environments.

## Established measurements

| Condition | Produced audio | Video observation |
|---|---:|---|
| Full CPU surface scaling/posting | approximately 14–18 kframes/s | guest submits about 70 fps |
| Present every fourth guest frame | approximately 28–32 kframes/s | about 17.5 presented fps |
| Opened AAudio stream | 48,000 frames/s required | burst size 192 frames |

The core produces stereo batches of 800 frames. These values establish a severe sustained
production deficit and a material presentation cost. They do not independently prove guest
time; the challenged specification requires a guest timer workload.

## Bounded telemetry validation

Commit `1ae7811` was built as APK
`8d023f9abc83a6e9128b4ae1906a729bacb23548f063715f32cb8603cafaa84d`, installed without
clearing app data, and run against the isolated clone. After boot settled, representative
one-second intervals reported:

| Metric | Settled observation |
|---|---:|
| Emulator run calls | about 36/s |
| Produced/consumed audio | about 28,800 frames/s |
| Missing audio | about 22,800–23,200 frames/s |
| Underrun callbacks | about 130–138/s |
| Submitted guest frames | about 36/s |
| Presented frames | about 9/s |
| PCM samples at numeric saturation | 0 |
| Surface post failures | 0 |

This confirms sustained emulation starvation. The reported clipping is not explained by PCM
numeric saturation at the host boundary; repeated AAudio underruns and inserted silence are
the measured cause of the broken output. The clone session was exited through RoboWindows'
own Exit control and logged `guest stopped cleanly`.

## Video decoupling result

The emulator callback was changed to publish into a bounded latest-frame mailbox, while a
separate presenter scales and posts a maximum 960-pixel-wide letterboxed buffer at up to
30 fps. On the same clone and device:

| Metric | Before | After |
|---|---:|---:|
| Emulator run calls | about 36/s | 68–70/s |
| Produced audio | about 28,800 frames/s | 54,400–56,000 frames/s |
| Presented video | about 9 fps | 30–31 fps |
| Foreground underruns | about 130–138/s | 0/s before queue saturation |
| Surface post failures | 0 | 0 settled; 1 during tested surface recreation |

The presenter recovered after Home/background/surface loss and returned to 30–31 fps without
a crash. That lifecycle test also proved the old AAudio stream keeps consuming while the VM
is paused: the full legacy queue drained and underruns began before resume. Audio suspension,
queue bounding, and fresh prebuffering are therefore required next.

Audio production now exceeds 48 kHz and the legacy queue reaches its 131,072-frame capacity.
This is not an audio success result: the legacy overflow path discards old samples and must be
replaced before listening or timing acceptance. Video decoupling itself passes the display
cadence checkpoint without making presentation the guest clock.

## Frontend throttle contract correction

The custom frontend incorrectly returned a fixed 60 Hz from
`RETRO_ENVIRONMENT_GET_THROTTLE_STATE` while the core advertised a 70.087 Hz VGA run rate.
DOSBox Pure therefore submitted `48000 / 60 = 800` frames per run, creating the measured
54–56 kHz overflow once video stopped blocking emulation. Returning unsupported lets the
pinned core use its current advertised rate, as intended by its fallback contract.

With the correction, 70 run calls produced about 47,941 frames in 1.01 seconds and the queue
remained bounded initially. Under settled Windows workload the conservative core later fell
to about 68 calls and 46,571 produced frames per second, causing roughly 1,500–1,800 missing
frames/s. The producer-rate inflation is fixed, but the target still needs a sustainable CPU
profile; prebuffering must not be used to conceal this persistent 3% deficit.

## SPSC audio and lifecycle result

The legacy mutex/sample ring was replaced by an 8,192-frame stereo SPSC queue with an
explicit stopped/prebuffering/playing/suspended/recovering state model. The AAudio callback
now performs no mutex acquisition, allocation, or logging. Overflow drops new frames with an
explicit counter; underflow inserts bounded silence with callback and missing-frame counters.

On the isolated clone, foreground behavior reproduced the corrected producer evidence:
47,940–47,941 frames per second while the VM sustained 70 calls, then about 46,571 frames/s
and visible underruns when it fell to 68 calls. Queue capacity was respected and no frames
were silently overwritten.

During a Home/background/resume test:

- runtime/audio state changed to `surface_lost`/`suspended`;
- produced and consumed audio were both zero throughout settled background intervals;
- queue minimum and maximum were both zero;
- resume cleared the old generation and started only after a fresh 2,400-frame prebuffer;
- playback returned with zero initial underruns and the presenter recovered to 30–31 fps;
- the clone stopped cleanly through the product-owned Exit control.

This closes the stale replay and lifecycle-consumption defect. It does not close the
sustainable-speed defect; cycle/profile tuning must eliminate the measured 68/70-call
foreground shortfall.

## Absolute scheduler and profile comparison

The host originally scheduled each `retro_run` relative to that iteration's start. Any
one-off overrun from Android scheduling or telemetry was permanently lost, reducing average
cadence to 68–69 calls/s even when subsequent iterations had spare time. The corrected loop
advances an absolute deadline at the core-advertised frame period and permits one bounded
catch-up interval; it resets the deadline on pause so resume cannot fast-forward stale time.

With the corrected scheduler, both `normal/fixed 20000` and the existing `auto` profile ran
the isolated clone for 30-second comparisons with:

- 71 calls and about 48,625 produced frames per 1.012–1.013 second reporting interval
  (approximately 48,000 frames/s when normalized);
- zero underruns, missing frames, dropped frames, or saturated samples after startup;
- stable queue depth instead of progressive fill or drain;
- 30–31 presented fps and no settled surface post failures.

The `auto` queue stabilized around 4.6–5.6k frames and did not invoke the crashing direct
dynamic profile during this isolated test. A later boot of the installed Windows 98 profile
failed with `DynrecCore: illegal option in dyn_grp4_eb`; the same disk reached the desktop
with `core=normal` and subsequently ran Age of Empires II and SimCity 2000. Therefore
`normal` is now the Windows default and `auto` is exposed only as an experimental preset.
Independent thermal-soak and broader application-compatibility gates remain outstanding.

## Known failure and controlled variables

- Direct ARM64 `core=dynamic` selection caused an immediate native SIGSEGV and is not a
  validated profile.
- Windows Device Manager resources already match the generated SB16 configuration; changing
  drivers is not justified by current evidence.
- Valid measurements require RoboWindows full-screen and foreground, the tablet awake and
  unlocked, and audio not interrupted.
- A previously observed production collapse occurred while the UI showed the VM paused;
  paused/background intervals must be excluded.
- User-owned ISO, registration data, disk images, PCM, and framebuffer content are excluded
  from this repository and diagnostic reports.

## Protected backup status

The installed-state backup is gzip-valid and its individual artifacts are verified against
`backups/win98-installed-20260810/SHA256SUMS` from within that directory. Device performance
tests must use a writable clone. Exact pre/post image equality is required only for a proven
pre-execution crash; booted Windows tests instead check clone filesystem health and expected
persistence.

## 2026-09-06 tablet storage cleanup

The retired real-time test area and an unreferenced debug Windows-import area were permanently
removed through the debug application's sandbox after a Machines-screen preflight confirmed no
active session and no metadata reference. The protected machine library remained present at
5,023,321,088 bytes before and after each deletion. The two target areas totaled
2,756,698,112 bytes (about 2.57 GiB); no app-storage reset occurred, and RoboWindows remained
at Machines without booting a guest. The protected host backup remains the recovery source.

## Dynamic cadence and sustained tablet profile

The frontend now accepts `RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO` updates and retimes its
absolute `retro_run` deadline when DOSBox changes guest cadence (for example, 70.087 Hz to
60 Hz). This prevents the frontend from continuing to drive a 70 Hz schedule while DOSBox
produces 800 audio frames per call at 60 Hz.

The presenter is capped at approximately 15 fps on the SM-T500, and the audio SPSC queue is
16,384 stereo frames with a 200 ms prebuffer. A rebuilt APK was tested on the isolated clone
with `normal-12000.conf`: the focused validation sustained 70/71 calls per interval,
approximately 48 kHz production and consumption, 15–16 presented fps, and zero underruns,
missing frames, drops, saturation, or stream errors in the captured interval. A longer
dynamic-cadence run crossed the timing transition without the former queue overflow; one
short scheduler underrun was observed before the larger cushion was applied. The final
five-minute foreground soak with the larger cushion completed with zero underruns, missing
frames, drops, saturation, or stream errors and stopped cleanly through the product UI.
