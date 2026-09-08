# Implementation Plan: Experimental Machine Copies

Duplicate only stopped machine runtime media into a fresh app-private profile directory. Hash the
source and staged target, atomically publish the target disk, regenerate the launch configuration,
and commit metadata last. Immutable media remains read-only; attached writable runtime media is
also independently copied. A profile role and fixed-cycle fields migrate existing entries safely.

The product UI offers copy creation only for stable machines and fixed normal-core trials only for
the new experimental machine. It exposes the conservative 10k, 12k, and 14k profiles plus 20k
and 30k trials for games that need more CPU time; all retain the normal core and recover to the
safe 12,000-cycle configuration after interruption. No device experiment is authorized by this
change.

The copy worker reports bounded byte progress across source hashing, staged copying, and target
hashing. The RoboWindows UI maps that work to a determinate progress bar and explicit stage text;
metadata finalization is shown separately rather than appearing to stall at 100%.

Deletion stages the selected profile directory under the app-private machine root, removes the
profile index entry, and then deletes the staged directory. If profile persistence fails, the
directory is restored before reporting failure. RoboWindows requires an explicit confirmation;
adding the control never deletes a machine.

Rollback is deletion of an unused experimental profile/directory only; never delete or replace the
source machine. Dynamic recompilation remains a separate, clone-only investigation under Feature
008, using Feature 002's performance and audio evidence.
