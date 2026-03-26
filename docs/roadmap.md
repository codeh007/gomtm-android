# Roadmap

This document records the active direction for the public `gomtm-android` repository.

## Direction reset

Older bootstrap-only wording is superseded.

The current source of truth is:

- `gomtm-android` is the public Android app host
- `gomtm` remains the swarm kernel / Android AAR producer
- swarm-first is the correct order
- worker control, group control, and reward automation come later
- monorepo `apps/android/` is migration material only, not the future host repo

## Phase 1: public swarm host shell

Goals:

- keep the public Android app repository buildable and releasable via GitHub Actions
- expose a visible runtime shell for gomtm swarm integration
- support optional AAR consumption without requiring the AAR to exist in-repo
- make the app honest about bound vs unbound swarm runtime state

Deliverables:

- Android host screen for runtime state inspection
- reflection-based bridge wrapper so the app can compile with or without the AAR
- CI / release steps that can optionally download a swarm AAR into `app/libs/`

## Phase 2: gomtm swarm AAR consumption

Goals:

- consume a versioned gomtm-produced Android AAR
- display real runtime information such as node state, peer id, bootstrap address, and logs
- keep the app shell repo free from duplicated swarm implementation

Upstream `gomtm` still needs to settle:

- stable Android bridge API
- downloadable AAR publication path
- artifact naming and compatibility contract

## Phase 3: node lifecycle UX

After Phase 2:

- start / stop controls for the swarm node
- runtime configuration entry points
- connection diagnostics and structured logs
- bootstrap / relay onboarding

## Phase 4: worker and control-plane surfaces

Only after swarm substrate is stable:

- worker metadata
- heartbeat / presence surfaces
- minimal control-plane views
- later group-control integration

## Explicit non-goals for the current phase

The current phase does **not**:

- re-implement libp2p in Kotlin
- continue feature development in monorepo `apps/android/`
- promise that a public stable gomtm AAR feed already exists
- pull reward automation concerns into the Android host shell
