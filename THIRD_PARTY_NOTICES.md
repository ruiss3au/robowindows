# Third-party notices

RoboWindows is licensed under GPL-2.0-or-later. The following third-party
components are used by the source tree or its reproducible build process.

## Distributed runtime component

### DOSBox Pure

- Upstream: <https://github.com/schellingb/dosbox-pure>
- Pinned revision: `7f6e8fb7385fa446d1444d671063268520bf9b54`
- License: GPL-2.0-or-later
- Copyright: the DOSBox Pure contributors and the DOSBox Team
- Use: fetched into ignored `third_party/`, patched by
  `patches/dosbox-pure/0001-preserve-conf-on-guest-reboot.patch`, and compiled
  into the Android application

The upstream `LICENSE`, `DOSBOX-AUTHORS`, and other attribution files remain in
the pinned source checkout. `scripts/fetch-sources.sh` obtains the exact source;
`lock/sources.lock` and the project patch provide the corresponding-source
record for RoboWindows builds.

## Tracked build component

### Gradle Wrapper

- Upstream: <https://github.com/gradle/gradle>
- Version: 9.1.0
- License: Apache-2.0; see `LICENSES/Apache-2.0.txt`
- Use: `gradle/wrapper/gradle-wrapper.jar` bootstraps the pinned Gradle
  distribution whose URL and SHA-256 are recorded in
  `gradle/wrapper/gradle-wrapper.properties`

Gradle's notice states that it includes software developed by the Apache
Software Foundation and identifies Groovy, SLF4J, JUnit, JCIFS, and Apache
HttpClient as software included in the full Gradle distribution. RoboWindows
tracks only the wrapper JAR; the distribution is downloaded into an ignored
cache.

## Reference-only source checkouts

These pinned repositories support architecture research but are not linked,
copied into RoboWindows, or distributed in its APK:

| Component | Revision | License |
|---|---|---|
| [DOSBox Pure Unleashed](https://github.com/schellingb/dosbox-pure-unleashed) | `4a11412248ca4c862751a7d9e6818023795031e9` | GPL-2.0-or-later |
| [ZillaLib](https://github.com/schellingb/ZillaLib) | `a2796bfe0faebe3e5de14b75d6b45866f1576f14` | zlib |

Their license files remain in their ignored pinned checkouts. No guest operating
system, application, driver, firmware ROM, product key, disk image, or ISO is
part of this repository or the application.

## CI-only tools

GitHub Actions uses commit-pinned releases of `actions/checkout`,
`actions/setup-java`, and `android-actions/setup-android`. These MIT-licensed
actions execute in CI and are not redistributed with RoboWindows.
