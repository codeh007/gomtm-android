# Roadmap

This document records future evaluation topics for the public `gomtm-android` repository.

## What this phase is for

The current phase is only meant to establish a minimal public Android repository baseline with a HelloWorld app and clear collaboration boundaries.

## Future items to evaluate

### 1. Legacy Android migration

Evaluate whether and how the legacy `apps/android/` project should be migrated into this repository.

Questions to answer later:
- what code should move first
- what should remain behind temporarily
- how to preserve reviewability during migration

### 2. Formal signing and release

Evaluate the eventual production signing and release approach, including:
- keystore handling
- secret management
- release approval boundaries
- artifact publication flow

### 3. Independent Go/AAR build chain

Evaluate how the Go/AAR build chain can be separated and maintained independently from the initial HelloWorld bootstrap setup.

Questions to answer later:
- where the build responsibilities should live
- how generated artifacts should be versioned
- how CI should validate the chain without coupling everything too early

## Not included in the current phase

The current phase does **not** do any of the following:

- migrate the legacy `apps/android/` project
- implement formal signing or production release wiring
- define or build the Go/AAR pipeline
- add CI or Release workflow files ahead of the dedicated follow-up work

These topics are intentionally left as future evaluation items rather than present commitments.
