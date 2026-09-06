# Implementation Plan: Experimental Machine Copies

Duplicate only stopped machine runtime media into a fresh app-private profile directory. Hash the
source and staged target, atomically publish the target disk, regenerate the launch configuration,
and commit metadata last. Immutable media remains read-only; attached writable runtime media is
also independently copied. A profile role and fixed-cycle fields migrate existing entries safely.

The product UI offers copy creation only for stable machines and fixed normal-core trials only for
the new experimental machine. An active-session marker restores the experimental profile's safe
12,000-cycle configuration after interruption. No device experiment is authorized by this change.

Rollback is deletion of an unused experimental profile/directory only; never delete or replace the
source machine. Dynamic recompilation remains a separate, clone-only investigation under feature
002.
