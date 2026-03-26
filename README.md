# gomtm-android

Public Android app host repository for gomtm.

## Current source of truth

This repository is the long-term public Android app host for gomtm.

The active architecture is:

- `gomtm` remains the swarm kernel / Android AAR producer
- `gomtm-android` is the public Android app host, CI, release, and APK distribution repo
- swarm-first comes before worker orchestration, group control, or reward automation
- legacy `apps/android/` in the monorepo is migration material only, not the future app host

## What is implemented in this repo today

This repo now ships a **swarm-first host shell** instead of a generic HelloWorld placeholder.

That means:

- the app can build and release publicly even when no gomtm swarm AAR is present
- the app contains a runtime probe screen that detects whether a gomtm bridge is actually bound
- the app uses reflection-based probing so it does not fake a compile-time dependency on an AAR that upstream does not yet publish stably
- GitHub Actions can optionally download a prebuilt gomtm swarm AAR into `app/libs/` before building

## What this repo is responsible for

- Android UI and product shell
- public pull-request workflow
- GitHub Actions CI and release automation
- APK publication
- optional consumption of a gomtm-produced swarm AAR

## What this repo is not responsible for

- re-implementing libp2p or swarm logic in Kotlin
- reviving or continuing development in monorepo `apps/android/`
- pretending that a stable public gomtm AAR feed already exists
- pulling reward-automation business logic into the Android host shell

## Validation policy

- use GitHub Actions as the build / validation path
- use pull requests for functional changes
- do not treat local Android builds as the acceptance path for this phase

## Optional AAR consumption

If CI or a maintainer provides a gomtm swarm AAR, it should be placed into `app/libs/`.

The host app then probes for known bridge classes at runtime and reports:

- whether a bridge was detected
- which bridge class was found
- whether the lifecycle surface looks like `node`, `worker-legacy`, or `probe-only`
- runtime state, peer id, bootstrap address, last error, and drained logs when exposed by the bridge

If no AAR is present, the app still builds and clearly reports that it is running as a **public host shell only**.

## Related docs

- [Roadmap](docs/roadmap.md)
- [Swarm runtime integration](docs/swarm-runtime-integration.md)
