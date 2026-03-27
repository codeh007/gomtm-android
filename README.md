# gomtm-android

Public Android swarm node app for gomtm.

## Current source of truth

This repository is the long-term public Android app host for gomtm.

The active architecture is:

- `gomtm` remains the swarm kernel / Android AAR producer
- `gomtm-android` is the public Android node app host, CI, release, and APK distribution repo
- swarm-first comes before worker orchestration, group control, or reward automation
- legacy monorepo `apps/android/` is migration material only, not the future app host

## What this repo is responsible for

- Android UI and product shell
- starting and stopping a real gomtm swarm node runtime
- showing runtime state, peer id, bootstrap address, logs, and discovered peers
- GitHub Actions CI and release automation
- APK publication
- consuming a **published, pinned** `gomtm-swarm-android.aar`

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

## Related docs

- [Roadmap](docs/roadmap.md)
- [Swarm runtime integration](docs/swarm-runtime-integration.md)
