# gomtm-android

Public Android repository bootstrap for gomtm.

## Repository goal

This repository exists to establish a minimal public Android repository baseline so future pull-request, GitHub Actions, and GitHub Release validation can be introduced cleanly before the legacy Android codebase is migrated.

This repository is intentionally starting with a minimal HelloWorld app to validate the public GitHub PR/CI/Release workflow before migrating the legacy Android project.

## Current status

- The current app is a minimal HelloWorld Android app.
- It is a repository bootstrap step that creates the minimum baseline for later workflow validation.
- It does **not** mean the legacy `apps/android/` application has already been migrated here.
- Migration planning and productionization are intentionally deferred to later phases.

## Collaboration model

- All functional changes should go through pull requests.
- Treat `main` as review-driven history, not a place for direct feature pushes.
- Keep scope small and explicit so the repo can build the minimum baseline for future public PR/CI/release validation incrementally.

## Security boundaries

Do **not** commit any of the following:

- keystore files
- `local.properties`
- signing credentials
- API tokens, secrets, or any other sensitive material

If future signing or secret-based workflows are introduced, they must be handled through approved repository or release automation mechanisms rather than committed files.

## Verification

- This phase establishes the bootstrap baseline for future GitHub-based validation; it does **not** mean GitHub Actions checks or release automation are already implemented here.
- Do **not** rely on local builds as the project acceptance path for this phase.
- Do **not** run local Android build, test, emulator, or release steps as part of this repository bootstrap task.

## Out of scope for this phase

This phase does **not** yet implement:

- legacy `apps/android/` migration
- formal signing and production release setup
- independent Go/AAR build-chain extraction
- CI or Release workflow authoring beyond documenting the intended direction

See [`docs/roadmap.md`](docs/roadmap.md) for future evaluation items.
