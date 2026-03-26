# Swarm runtime integration

`gomtm-android` is being prepared to consume a gomtm-produced Android swarm AAR without pretending that the upstream publication contract is already finished.

## Current approach

The app uses a **reflection-based integration seam**:

- Gradle will include any optional `*.aar` dropped into `app/libs/`
- the Kotlin host shell does not compile against hard AAR symbols
- `SwarmRuntime` probes for known bridge class names at runtime
- if no bridge is present, the app still ships and clearly reports that it is running as a host shell only

## Why this is the right temporary shape

Right now, the public upstream `gomtm` releases do not expose a stable downloadable swarm AAR asset that this repo can treat as a guaranteed dependency.

So the host repo should optimize for honesty:

1. keep the public Android repo buildable
2. avoid inventing a second swarm implementation in Kotlin
3. avoid hard-wiring to a fake or private-only artifact path
4. preserve a narrow seam that a real gomtm AAR can plug into later

## What upstream gomtm still needs to provide

Before this repo can become a real runtime consumer, upstream gomtm work still needs to settle:

- artifact naming and versioning
- bridge API stability
- publication path for downloadable AAR artifacts
- compatibility rules between host APK versions and runtime AAR versions

## Current host shell contract

Today the public app shell can safely expose:

- whether a gomtm swarm bridge was detected
- which bridge class was found
- whether the lifecycle surface looks like `node`, `worker-legacy`, or `probe-only`
- current runtime state, peer id, bootstrap address, and last error if the bridge exports those methods
- drained runtime logs when available

This gives the repo a truthful and reviewable intermediate state instead of a fake finished integration.
