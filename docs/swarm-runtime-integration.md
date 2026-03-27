# Swarm runtime integration

`gomtm-android` consumes a **published, pinned** gomtm Android swarm AAR.

## Current approach

The public Android app host is no longer treated as a generic host shell.

Current contract:

- GitHub Actions downloads a specific `gomtm-swarm-android.aar`
- GitHub Actions verifies its SHA256 before Gradle runs
- the app binds to the current gomtm node runtime surface
- the UI shows real runtime state, peer id, bootstrap address, logs, and discovered peers
- the APK release records which gomtm AAR version / URL / SHA256 it consumed

## Upstream gomtm requirements

`gomtm` must publish these release assets:

- `gomtm-swarm-android.aar`
- `gomtm-swarm-android.json`

The metadata file must at least expose:

- gomtm version
- gomtm commit
- gomtm source ref
- AAR sha256
- upstream sing-box repo/ref/commit
- published timestamp

## Downstream rules in this repo

This repo must not:

- consume `latest` implicitly
- skip checksum validation
- release an APK without recording the gomtm AAR provenance it was built from

This repo must:

1. pin `gomtm_swarm_aar_url`
2. pin `gomtm_swarm_aar_version`
3. pin `gomtm_swarm_aar_sha256`
4. download the AAR into `app/libs/`
5. validate the checksum
6. run Gradle only after the AAR contract is satisfied

## UI contract

The public app UI must expose:

- start node
- stop node
- runtime state
- peer id
- bootstrap address
- discovered peers list
- last error
- recent logs

No part of the UI should pretend that a node is running when the bound runtime is absent or misconfigured.
