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

- a thin single-Activity WebView host shell for the public swarm node app
- starting and stopping a real gomtm swarm node runtime through the foreground service
- exposing runtime/config/discovery host primitives to `/dash/p2p` through a minimal Android <-> JS bridge
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

The app consumes a published `gomtm-swarm-android.aar` and keeps the runtime surface behind a WebView host boundary:

- `startNode(baseDir, config)`
- `stopNode()`
- `getState()`
- `getPeerID()`
- `getBootstrapAddr()`
- `getLastError()`
- `getDiscoveredPeers()`
- `drainLogs()`

The product UI lives in the shared `/dash/p2p` Web page. The Android shell is responsible for loading that entry URL and exposing host primitives, not for keeping a parallel native dashboard.

## Release trigger

This repository no longer relies on repository Actions variables to discover the next swarm runtime.

The committed file `app/libs/gomtm-swarm-android.json` now pins:

- the gomtm swarm AAR version
- the GitHub release asset URL for the AAR asset
- the GitHub release asset URL for the metadata asset
- the expected SHA256

Updating that file on `main` is the canonical way to refresh CI against a newly published gomtm AAR. Formal APK releases use an explicit `v*` tag or manual `Release` dispatch, and the Gradle app version metadata is derived from that release tag so the published APK provenance, release page, and in-app version stay aligned.

## Host shell shape

The current Android shell has been intentionally reduced to:

- one `MainActivity`
- one `WebView host shell` in `activity_main.xml`
- one foreground-service-owned swarm runtime
- one screen capture permission entry for native remote capabilities
- one minimal Android <-> JS bridge in `GomtmWebViewBridge`

Current package boundaries:

- `com.gomtm.swarm.runtime` only hosts the thin AAR runtime facade and shell-facing DTOs
- `com.gomtm.swarm.platform.*` only hosts Android native components and device capability adapters
- `com.gomtm.swarm.shell` only hosts local shell persistence
- `com.gomtm.swarm.web` only hosts the WebView bridge surface
- the old catch-all `com.gomtm.swarm.swarm` package is no longer the canonical source layout

`MainActivity` now loads the shared `BuildConfig.GOMTM_UI_DASH_P2P_URL` Web entry directly. If that page cannot load, the only native fallback is a minimal error text surface; the old native runtime dashboard is no longer a product UI.

## Related docs

- [Roadmap](docs/roadmap.md)
- [Swarm runtime integration](docs/swarm-runtime-integration.md)
