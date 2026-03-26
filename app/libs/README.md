Place optional prebuilt gomtm swarm AAR artifacts here.

Expected direction:
- `gomtm` produces the Android swarm AAR
- `gomtm-android` consumes it from `app/libs/` during CI or release builds

This directory is intentionally kept in-repo so GitHub Actions can download the AAR here before Gradle runs.
Do not commit secrets or unrelated binaries.
