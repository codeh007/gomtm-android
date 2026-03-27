# Roadmap

## Current milestone

Deliver a public Android node app that can:

- consume a published, pinned gomtm Android swarm AAR
- start a real gomtm swarm node runtime
- show peer id, bootstrap address, logs, and discovered peers
- ship through GitHub Actions with APK provenance recorded

## Explicit non-goals for this milestone

- rebuilding swarm logic in Kotlin
- extending monorepo `apps/android/`
- reward automation or group-control business logic
- local Android builds as the source of acceptance truth

## Next milestone after swarm-first

After the swarm node base is proven stable on real Android devices:

- improve runtime persistence / lifecycle handling
- add better peer/session diagnostics
- extend worker control capabilities on top of the swarm base
