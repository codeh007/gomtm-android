# Swarm runtime integration

`gomtm-swarm` consumes a **published, pinned** gomtm Android swarm AAR.

## Current approach

The public Android app host is no longer treated as a generic host shell.

Current contract:

- GitHub Actions reads the committed pin manifest `app/libs/gomtm-swarm-android.json`
- GitHub Actions downloads the specific `gomtm-swarm-android.aar` referenced by that manifest
- GitHub Actions verifies its SHA256 before Gradle runs
- the app binds to the current gomtm node runtime surface
- the app boots into a compact native runtime shell owned by `MainActivity`
- the foreground service owns runtime start/stop while the Activity renders the current runtime surface
- the native shell exposes the current peer suffix, runtime state, shell version, and screen capture permission action
- the APK release records which gomtm AAR version / URL / SHA256 it consumed

## Upstream gomtm requirements

`gomtm` must publish these release assets:

- `gomtm-swarm-android.aar`
- `gomtm-swarm-android.json`

The pinned manifest in this repo must at least expose:

- gomtm version
- gomtm commit
- gomtm source ref
- AAR sha256
- published timestamp
- GitHub release AAR asset URL
- GitHub release metadata asset URL

The public app only consumes the current gomtm swarm runtime contract. Extra provenance fields are allowed, but pin changes should feed CI truth, not silently create a formal public release.

## Downstream rules in this repo

This repo must not:

- consume `latest` implicitly
- skip checksum validation
- release an APK without recording the gomtm AAR provenance it was built from

This repo must:

1. commit `app/libs/gomtm-swarm-android.json`
2. pin `aar_url`
3. pin `metadata_url`
4. pin `version`
5. pin `sha256`
6. download the AAR into `app/libs/`
7. validate the checksum
8. run Gradle only after the AAR contract is satisfied
9. publish formal app releases only through an explicit `v*` tag or manual release dispatch

## UI contract

The public app UI must expose:

- start node
- stop node
- runtime state
- peer suffix and runtime diagnostics derived from the bound node
- permission diagnostics
- native shell status cues for the foreground-service-managed runtime

No part of the UI should pretend that a node is running when the bound runtime is absent or misconfigured.

The Android shell must stay thin: do not reintroduce a WebView host, a second Activity, or a parallel bootstrap form just to host swarm pages.
