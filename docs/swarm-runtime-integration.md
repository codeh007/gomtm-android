# Swarm runtime integration

`gomtm-android` consumes a **published, pinned** gomtm Android swarm AAR.

## Current approach

The public Android app host is no longer treated as a generic host shell.

Current contract:

- GitHub Actions downloads a specific `gomtm-swarm-android.aar`
- GitHub Actions verifies its SHA256 before Gradle runs
- the app binds to the current gomtm node runtime surface
- the app boots into an embedded HTML+JS bootstrap page inside a single WebView host
- that bootstrap page shows real runtime state, peer id, bootstrap address, logs, permissions, and discovered peers
- external gomtmui pages open inside the same WebView, but without keeping the host bridge exposed
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
- published timestamp

The metadata file must not keep historical `sing-box` upstream provenance fields. The public app only consumes the current gomtm swarm runtime contract.

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
- permission diagnostics
- discovered peers list
- last error
- recent logs

No part of the UI should pretend that a node is running when the bound runtime is absent or misconfigured.

The Android shell must stay thin: do not reintroduce a second Activity or a parallel native form just to host swarm pages.
