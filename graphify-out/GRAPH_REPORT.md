# Graph Report - /workspace/gomtm-android  (2026-04-08)

## Corpus Check
- Corpus is ~7,108 words - fits in a single context window. You may not need a graph.

## Summary
- 225 nodes · 219 edges · 21 communities detected
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 6 edges (avg confidence: 0.76)
- Token cost: 0 input · 0 output

## God Nodes (most connected - your core abstractions)
1. `SwarmRuntime` - 23 edges
2. `MainActivity` - 16 edges
3. `AndroidScreenStreamHost` - 16 edges
4. `ScreenStreamSession` - 15 edges
5. `GomtmAccessibilityService` - 13 edges
6. `Swarm Runtime Bridge Adapter` - 10 edges
7. `AndroidRemoteControlOps` - 9 edges
8. `RemoteControlOps` - 8 edges
9. `SwarmRuntimeTest` - 8 edges
10. `Pinned Swarm AAR Dependency` - 7 edges

## Surprising Connections (you probably didn't know these)
- `Runtime UI Contract` --semantically_similar_to--> `Main Activity Runtime Surface`  [INFERRED] [semantically similar]
  docs/swarm-runtime-integration.md → app/src/main/java/com/gomtm/android/MainActivity.kt
- `No Kotlin Swarm Rewrite Goal` --rationale_for--> `Swarm Runtime Bridge Adapter`  [INFERRED]
  docs/roadmap.md → app/src/main/java/com/gomtm/android/swarm/SwarmRuntime.kt
- `Pinned AAR Integration Contract` --semantically_similar_to--> `Public Android Node Host`  [INFERRED] [semantically similar]
  docs/swarm-runtime-integration.md → README.md
- `Pinned Swarm AAR Dependency` --references--> `Swarm Runtime Bridge Adapter`  [INFERRED]
  app/build.gradle.kts → app/src/main/java/com/gomtm/android/swarm/SwarmRuntime.kt
- `Host Identity Application ID Test` --references--> `Pinned Swarm AAR Dependency`  [INFERRED]
  app/src/test/java/com/gomtm/android/web/HostIdentityContractTest.kt → app/build.gradle.kts

## Hyperedges (group relationships)
- **Remote Control Request Pipeline** — swarmruntime_bridge_adapter, remotectl_command_protocol, androidremotecontrolops_android_adapter, gomtmaccessibilityservice_remote_input_service, androidscreenstreamhost_media_projection_host [EXTRACTED 1.00]
- **Runtime Surface Control Loop** — mainactivity_runtime_surface, swarmruntime_bridge_adapter, swarmstatus_runtime_snapshot, swarmnodeconfig_bootstrap_config [EXTRACTED 1.00]
- **Pinned AAR Release Contract** — appbuild_pinned_aar_dependency, readme_public_android_node_host, integration_pinned_aar_contract, roadmap_swarm_first_milestone [INFERRED 0.82]

## Communities

### Community 0 - "Swarm Runtime Bridge"
Cohesion: 0.08
Nodes (1): SwarmRuntime

### Community 1 - "Remote Control Payloads"
Cohesion: 0.08
Nodes (12): Error, RemoteControlActionPayload, RemoteControlCapabilityState, RemoteControlCommandRequest, RemoteControlCommandResponse, RemoteControlCommandResult, RemoteControlPermissionState, RemoteControlScreenshotPayload (+4 more)

### Community 2 - "Accessibility Input Service"
Cohesion: 0.1
Nodes (1): GomtmAccessibilityService

### Community 3 - "Runtime Bridge Fakes"
Cohesion: 0.1
Nodes (4): FakeConfig, FakeNodeBridge, FakeThreeArgOptionsBridge, FakeTwoArgOptionsBridge

### Community 4 - "Main Activity Surface"
Cohesion: 0.11
Nodes (1): MainActivity

### Community 5 - "Screen Stream Session"
Cohesion: 0.11
Nodes (2): DisplayMetricsSnapshot, ScreenStreamSession

### Community 6 - "Screen Stream Host"
Cohesion: 0.12
Nodes (1): AndroidScreenStreamHost

### Community 7 - "Swarm Runtime Integration"
Cohesion: 0.21
Nodes (15): Android Remote Control Ops Adapter, MediaProjection Screen Stream Host, Discovered Peer Snapshot Parser, Accessibility-based Remote Input Service, Main Activity Runtime Surface, MainActivity Runtime Surface Contract Test, Remote Control Command Protocol Test, Remote Control Command Protocol (+7 more)

### Community 8 - "Screen Capture Foreground"
Cohesion: 0.2
Nodes (1): ScreenCaptureService

### Community 9 - "Android Remote Ops"
Cohesion: 0.2
Nodes (1): AndroidRemoteControlOps

### Community 10 - "AAR Release Contract"
Cohesion: 0.24
Nodes (10): Arm64-only APK Packaging Rationale, Pinned Swarm AAR Dependency, Host Identity Application ID Test, Pinned AAR Integration Contract, Thin Android Shell Constraint, Runtime UI Contract, Public Android Node Host, Swarm-first Product Direction (+2 more)

### Community 11 - "Remote Control Ops API"
Cohesion: 0.25
Nodes (1): RemoteControlOps

### Community 12 - "Swarm Runtime Tests"
Cohesion: 0.25
Nodes (1): SwarmRuntimeTest

### Community 13 - "Command Parser Tests"
Cohesion: 0.33
Nodes (1): RemoteControlCommandTest

### Community 14 - "Activity Contract Tests"
Cohesion: 0.4
Nodes (1): MainActivityContractTest

### Community 15 - "Discovered Peer Model"
Cohesion: 0.67
Nodes (1): DiscoveredPeer

### Community 16 - "Swarm Status Model"
Cohesion: 0.67
Nodes (1): SwarmStatus

### Community 17 - "Host Identity Tests"
Cohesion: 0.67
Nodes (1): HostIdentityContractTest

### Community 18 - "Swarm Node Config"
Cohesion: 1.0
Nodes (1): SwarmNodeConfig

### Community 19 - "Settings Gradle"
Cohesion: 1.0
Nodes (0): 

### Community 20 - "Root Build Gradle"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **29 isolated node(s):** `DisplayMetricsSnapshot`, `DiscoveredPeer`, `SwarmStatus`, `SwarmNodeConfig`, `RemoteControlCommandRequest` (+24 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Swarm Node Config`** (2 nodes): `SwarmNodeConfig.kt`, `SwarmNodeConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Settings Gradle`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Root Build Gradle`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AndroidScreenStreamHost` connect `Screen Stream Host` to `Screen Stream Session`?**
  _High betweenness centrality (0.015) - this node is a cross-community bridge._
- **What connects `DisplayMetricsSnapshot`, `DiscoveredPeer`, `SwarmStatus` to the rest of the system?**
  _29 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Swarm Runtime Bridge` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `Remote Control Payloads` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `Accessibility Input Service` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Runtime Bridge Fakes` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `Main Activity Surface` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._