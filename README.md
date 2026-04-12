# gomtm-swarm

Public Android swarm node app for gomtm, released under the product name `gomtm-swarm`.

## Current source of truth

This repository is the long-term public Android app host for gomtm.

The active architecture is:

- `gomtm` remains the swarm kernel / Android AAR producer
- `gomtm-android` is the repository host for the public Android node app `gomtm-swarm`, including CI, release, and APK distribution
- swarm-first comes before worker orchestration, group control, or reward automation
- legacy monorepo `apps/android/` is migration material only, not the future app host

## What this repo is responsible for

- a thin single-Activity native Android shell for the public swarm node app
- starting and stopping a real gomtm swarm node runtime through the foreground service
- showing the runtime surface state in the native shell, including peer suffix and permission state
- requesting screen capture permission for the native remote pipeline
- GitHub Actions CI and release automation
- APK publication
- consuming a **published, pinned** `gomtm-swarm-android.aar`
- using the committed pin manifest `app/libs/gomtm-swarm-android.json` as the CI truth surface for the pinned runtime

## What this repo is not responsible for

- re-implementing libp2p or swarm logic in Kotlin
- reviving or continuing development in monorepo `apps/android/`
- consuming an unpinned `latest` AAR
- pulling reward-automation business logic into the Android node host

## Validation policy

- use GitHub Actions as the build / validation path
- use pull requests for functional changes
- do not treat local Android builds as the acceptance path for this phase

## Runtime contract

The app consumes a published `gomtm-swarm-android.aar` and drives the current node runtime surface:

- `startNode(baseDir, config)`
- `stopNode()`
- `getState()`
- `getPeerID()`
- `getBootstrapAddr()`
- `getLastError()`
- `getDiscoveredPeers()`
- `drainLogs()`

The UI is expected to show real runtime data, not a host-shell placeholder.

## Release trigger

This repository no longer relies on repository Actions variables to discover the next swarm runtime.

The committed file `app/libs/gomtm-swarm-android.json` now pins:

- the gomtm swarm AAR version
- the GitHub release asset URL for the AAR asset
- the GitHub release asset URL for the metadata asset
- the expected SHA256

Updating that file on `main` is the canonical way to refresh CI against a newly published gomtm AAR. Formal APK releases use an explicit `v*` tag or manual `Release` dispatch.

## Host shell shape

The current Android shell has been intentionally reduced to:

- one `MainActivity`
- one compact native runtime surface in `activity_main.xml`
- one foreground-service-owned swarm runtime
- one screen capture permission entry for native remote capabilities

Current package boundaries:

- `com.gomtm.swarm.runtime` only hosts the thin AAR runtime facade and shell-facing DTOs
- `com.gomtm.swarm.platform.*` only hosts Android native components and device capability adapters
- `com.gomtm.swarm.shell` only hosts local shell persistence
- the old catch-all `com.gomtm.swarm.swarm` package is no longer the canonical source layout

There is no embedded `WebView`, no HTML bootstrap page, and no Android <-> JS host bridge in the current shell. There is also no second console Activity or parallel bootstrap form.

## Related docs

- [Roadmap](docs/roadmap.md)
- [Swarm runtime integration](docs/swarm-runtime-integration.md)
