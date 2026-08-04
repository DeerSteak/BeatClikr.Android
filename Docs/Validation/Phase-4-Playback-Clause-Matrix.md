# Phase 4 playback-clause verification matrix

The clause text in `Docs/Decisions/0002-Playback-Lifecycle-and-Outputs.md` remains authoritative. “Automated” distinguishes JVM policy tests, Android integration tests, and out-of-process instrumentation. “Focus record” and “route/interruption record” refer to `2026-08-01-Phase-4.4-Audio-Focus-Pixel-8a.md` and `2026-08-01-Phase-4.5-Routes-and-Interruptions-Pixel-8a.md` in this directory. The dated Pixel records are physical observations, not substitutes for unavailable-route qualification.

| Clause | Implementation | Automated evidence | Physical record | Deferred | Status |
| --- | --- | --- | --- | --- | --- |
| PL-001 | Application-scoped `PlaybackCoordinator` | `PlaybackCoordinatorTest`, `PlaybackDependencyGraphTest` | Focus record | — | Pass |
| PL-002 | Prepare/focus/stream/first-event start acknowledgement | `PlaybackCoordinatorTest.startPublishesAuthoritativeLifecycleInOrder`, `AudioEngineInstrumentedTest` | Built-in smoke | — | Pass |
| PL-003 | `PlaybackObservation` drives UI and secondary outputs | ViewModel tests, `SecondaryOutputCoordinatorTest` | Built-in smoke | Practice persistence is Phase 5 | Pass for Phase 4 |
| PL-004 | Typed failed/interrupted transport states | Coordinator and `InstantMetronomeViewTest` diagnostics tests | Built-in smoke | — | Pass |
| PL-005 | Session-tagged production engine boundary | Coordinator race tests, `PlaybackCoordinatorArchitectureTest` | — | — | Pass |
| PL-006 | Serialized mode replacement | `modeReplacementStopsOldModeBeforeStartingNewMode` | — | — | Pass |
| PL-007 | Foreground-service lifetime follows authoritative active sessions; backgrounding never starts playback | Service-controller and process-lifecycle tests | Background/lock observation pending | Phase 9 | Implemented; physical check pending |
| PL-008 | Tagged focus, route, and backend interruptions | Coordinator async interruption tests; route wiring integration | Focus record; route matrix owner-accepted | — | Pass |
| PL-009 | Interrupted/failed sessions require explicit restart | `interruptionStopsOnceAndNeverAutomaticallyResumes`, process-death tests | Screen/lock observation | — | Pass |
| PL-010 | Explicit restart creates a fresh origin/session | `explicitRestartCommitsEvidenceFromNewRoute`, process-death explicit-play test | Screen/lock observation | — | Pass |
| PL-011 | Media-playback foreground service shares the application coordinator | Service-controller tests and manifest/build checks | Background/lock observation pending | Phase 9 | Partial; media session pending |
| PL-012 | Persistent stop action never starts playback; media session remains pending | Command-handler tests | Notification observation pending | Phase 9 | Partial |
| PL-013 | Visible-and-playing keep-awake projection | `InstantMetronomeViewTest` keep-screen tests | Screen/lock observation | — | Pass |
| PL-014 | `AUDIOFOCUS_GAIN` acquired before start | Audio engine tests | Focus record | — | Pass |
| PL-015 | Exclusive focus; coexistence not guaranteed | Focus policy tests | YouTube Music paused | — | Pass |
| PL-016 | Lease abandoned after last stop/failure | Audio engine/coordinator tests | Empty focus stack after stop | — | Pass |
| PL-017 | Route classification and obtained stream evidence | Route tracker, backend, and startup tests | Built-in record; route matrix owner-accepted | — | Pass |
| PL-018 | Active route change interrupts and requires restart | Coordinator route tests; `PlaybackRouteWiringTest` | Route matrix owner-accepted | — | Pass |
| PL-019 | Bluetooth classified as supported/variable | Route and ViewModel tests | Route matrix owner-accepted | — | Pass |
| PL-020 | Frame scheduler prevents app-generated gaps/catch-up | Phase 2/3 qualification suites | Route matrix owner-accepted | — | Pass |
| PL-021 | Localized Bluetooth warning follows committed route | `InstantMetronomeViewTest.bluetoothWarningTracksAuthoritativeRouteInBothModes` | — | — | Pass |
| PL-022 | Renderer commit is musical authority | Renderer and coordinator committed-event tests | Built-in smoke | — | Pass |
| PL-023 | Secondary outputs consume committed frame events | `SecondaryOutputCoordinatorTest.committedBeatSchedulesHapticAndBoundedTorchPulse` | — | — | Pass |
| PL-024 | Correlated or explicitly unavailable presentation time | frame-session timestamp and committed-event tests | — | Calibration is Phase 7/8 | Pass for Phase 4 |
| PL-025 | Process visibility gates effects separately from foreground-service audio | Process lifecycle and secondary-output tests | Screen-off observation pending | Phase 9 | Implemented; physical check pending |
| PL-026 | Secondary disable/failure cannot alter audio | `SecondaryOutputCoordinatorTest` failure tests | — | — | Pass |
| PL-027 | Retained typed secondary failure | `secondaryFailureIsPublishedWithoutChangingPlayback` | — | — | Pass |
| PL-028 | Global stop on every top-level destination | Compact/expanded navigation instrumentation | — | — | Pass |
| PL-029 | Hidden compact mode stops once | `compactModeReplacementStopsHiddenModeOnceAndStartsAtFreshSession` | — | — | Pass |
| PL-030 | Internal Library/Playlist navigation preserves playback | `internalEditorsPickersSheetsAndFocusNavigationDoNotStopPlayback` | — | — | Pass |
| PL-031 | Explicit song selection replaces at tick zero | ViewModel replacement and navigation instrumentation tests | — | Practice transfer is Phase 5 | Pass for Phase 4 |

Combined event-capture behavior is tested functionally. Strict allocation assertions remain separate because HotSpot thread-allocation accounting for the inlined combined path varies with compiler state; this limitation is not promoted into a zero-allocation claim.
