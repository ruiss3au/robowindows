# Implementation Plan: Visible Build Identity

Set the shared Gradle version to 1.0-alpha/code 2; retain existing build suffixes,
application IDs and provenance behavior. Add alpha identity host assertions,
inspect both APK manifests and record limitations in an alpha handoff. Do not
publish or install over a running guest. Roll back behavior in a newer-version
forward fix, not by uninstalling or downgrading user data.

**Feature**: `006-visible-build-identity` | **Date**: 2026-09-06

## Design

- Resolve a display-safe revision during Gradle configuration from the explicit
  `ROBOWINDOWS_BUILD_REVISION` override or local Git metadata.
- Mark locally modified builds with `+dirty` and expose the value through
  `BuildConfig.SOURCE_REVISION`.
- Format the version and revision through a host-testable Java helper and render
  it immediately below the main-screen brand.

## Safety and Rollback

- Git inspection is read-only and does not include ignored build outputs.
- The revision is display metadata only; it does not change profile or guest
  formats.
- Revert the Gradle field, label helper, and second brand line to roll back.
