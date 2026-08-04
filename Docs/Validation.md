# Validation

## Verification layers

| Layer | Purpose | Environment |
| --- | --- | --- |
| Unit tests | Domain, state, and repository behavior | JVM |
| Lint | Android and Compose static checks | JVM/Android SDK |
| Debug assembly | Resource and dependency integration | Android SDK |
| Instrumentation | Android decoding and engine behavior | Emulator/device |
| Release bundle | Production resource, shrinker, and signing path | Trusted machine |
| Physical benchmark | Audible device-specific behavior | Reference hardware |

Passing a lower layer does not replace a higher one. Generated CI audio cannot validate production assets, and an emulator cannot certify speaker timing.

## Local checks

Use the JDK bundled with Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

With an Android 17 emulator running:

```bash
./gradlew --no-daemon connectedDebugAndroidTest
```

Debug builds use `com.bfunkstudios.beatclikr.debug`, so they can coexist with a signed production build without replacing it or its local data. Debug backup is disabled so an OS backup pass cannot kill a long-running instrumentation process.

Run the physical-device stress test for its default 30 minutes:

```bash
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AudioEngineStressInstrumentedTest
```

Override the duration from 1–60 minutes with `-Pandroid.testInstrumentationRunnerArguments.stressDurationMinutes=60`.

Instrumentation writes timing metrics to test output. Preserve a baseline in `benchmarks/` when the environment or timing implementation changes.

## Contract test convention

Tests that enforce approved contracts begin their method name with the normalized lowercase contract IDs they cover, joined by underscores, such as `mt001_mt003_supportedTempoBoundsAndDecimalBpmAreScheduledWithoutRounding`. Keep the descriptive suffix readable in Gradle and Android test reports. Use the same convention for `PL-*`, `PH-*`, and `TB-*` clauses.

`StandardMetronomeContractFixtures` contains resource-independent BPM, subdivision, tick, beat, and abstract sound-role cases corresponding to the representative iOS `MetronomeConstantsTests`, `GrooveTests`, and `MetronomeAudioBlockPlan` cases. `StandardMetronomeContractInstrumentedTest` runs those fixtures through the real Android engine.

`AccentContractFixtures` covers every Android odd-meter definition using groupings independently ported from the representative iOS `BeatPatternTests`, `GrooveTests`, and `MetronomeAudioBlockPlan` cases. `AccentContractInstrumentedTest` verifies both odd-meter timing units, additive-group accents, beat/rhythm sound selection, and alternate-sixteenth feedback against the real engine. `AccentContractTest` separately guards the Android pattern definitions on the JVM.

`PolyrhythmContractFixtures` represents expected events independently of `PolyrhythmGrid` and uses the same `beats` and `against` business meanings as iOS: `beats` is the displayed Rhythm count and `against` is the displayed Beat count. `PolyrhythmContractTest` exhaustively verifies all 225 supported ratios, event indices, shared origins, exact coincidences, and representative iOS duration formulas. `PolyrhythmContractInstrumentedTest` verifies complete real-engine cycles for boundary, equal, coprime, and shared-factor ratios.

`TempoRampContractTest` ports the iOS ramp choices and verifies the initial-beat rule, interval counting, reset behavior, subdivision exclusion, odd-meter accent counting, and the 240 BPM cap. Contract-tagged `MetronomeViewModelTest` cases verify instant-only application and restoration of the starting tempo on stop.

The Android-free 2.2 model suite verifies exact decimal tempo normalization, exact frame periods, inclusive tempo bounds, all regular subdivision and odd-meter unit mappings, every existing additive pattern, defensive immutability, all 225 polyrhythm ratios, exact cycle and stream intervals, session origins, and monotonic event sequences. These are JVM tests and require no emulator or Android runtime.

The Phase 2.3 pure-timeline suite verifies iOS-compatible absolute rounding, exact awkward periods, first-event searches, every standard subdivision, additive accents, alternate-sixteenth sound roles, mute continuity, adjacent and overlapping range boundaries, session resets, a twelve-hour maximum-density endpoint, all 225 polyrhythm ratios, coincident-frame merging, complete-cycle voice indices, and deterministic tempo-ramp transitions. The timeline tests require no emulator and do not exercise production playback.

The production command suite verifies stale-session rejection, strict ordering, complete configuration publication, phase-preserving standard updates, shared-origin polyrhythm restarts, stable transport identity, immediate phase-neutral mute, and two-stage sound preparation and publication through the coordinator and frame-publication boundary.

`PlaybackInputBoundaryTest` verifies that invalid external configurations become typed failures before render handoff, valid values remain available to production consumers, and unexpected implementation exceptions are not mislabeled as recoverable input errors.

The Phase 3.1 renderer suite verifies exact in-block offsets, adjacent-block waveform tails, reset and discontinuity handling, coincident and overlapping voices, final-stage saturating conversion, mute handling, silent failure blocks, and zero measured JVM allocation while rendering from a real Phase 2 timeline. Its architecture tripwire scans `FramePcmRenderer.kt` for common locking, sleeping, logging, file/database access, and thread-handoff tokens; it does not replace review of event-source implementations.

The Phase 3.2 sound suite verifies WAV structure and format failures, empty resources, leading-silence preservation, stereo downmixing, resampling, immutable snapshots, atomic replacement, cache regeneration and versioning, bank switching, concurrent preparation, and failure without partial publication. Its architecture test prevents decoder or cache access from returning to the current `AudioTrack` render loop.

The Phase 3.3 backend boundary suite verifies that mono and stereo obtained layouts preserve one renderer frame per output frame, duplicate mono samples into every channel, reject invalid ranges, and reuse the prepared interleaving buffer.

The Phase 3.3 stream-owner suite verifies renderer publication from obtained stream facts, absolute frame ranges, partial-write continuation in frame units, written-frame ownership after failure, typed owner failures routed to the responsible caller, halted-loop liveness, resync rejection before backend start, no-teardown recovery after failure, reset boundaries, idempotent stop, and zero measured allocation in the prepared owner render path.

The recovery composition test carries an active waveform tail into a discontinuity, synchronizes recovery to the written-frame boundary, counts the expired event arithmetically, and verifies the first recovered block contains only the new event. Backward recovery is rejected without changing frame ownership or resetting the renderer.

The prepared publication suite verifies that standard and polyrhythm timelines use the obtained sample rate, bind the approved beat and rhythm waveforms, preserve coincident mixing, and publish matching recovery state. An architecture tripwire prevents `copySamples()` from entering the stream owner or renderer publication path.

The `AudioTrackFrameSession` architecture tripwire prohibits pending-click queues, waveform enqueueing, and beat/rhythm trigger methods from entering the new Android frame driver. Physical-device qualification remains required after production selects this path.

The renderer suite verifies muted events do not increment role counters and failed blocks commit no counters. Publication tests carry nonzero origins into stream start, while the session tripwire requires a synchronous start result, publication-derived origin, sequence-stamped snapshot reads, session-relative block counts, and fixed-capacity failure recording without collection growth.

The publication-boundary suite accepts regular, additive, and polyrhythm requests, retains domain rejection causes, and translates missing sounds or invalid legacy inputs into typed failures. A ramp-derived fractional tempo verifies float-to-exact conversion at its expected output frame. Session architecture checks also require late-start cancellation and idempotent release so a rejected selection cannot leave a hidden render loop.

The production-selection tripwire requires standard and polyrhythm starts to use frame audio and prohibits the pending-click queue, waveform enqueue method, trigger methods, and legacy render runnable from the production output owner. Existing instrumentation metrics are backed by frame-rendered role counters, written-frame ownership, obtained stream facts, and the real `AudioTrack` underrun count.

Production and instrumentation compilation verify that no legacy Handler timeline or timing-delegate API remains. Standard and polyrhythm device helpers consume frame-rendered events from the same engine port used by production.

The frame-session tripwire requires one reusable timestamp holder, captures written/presented frame correlation into the consistent snapshot, and requires an advancing underrun count to skip timestamp-estimated missing presentation frames before owner resynchronization. Pure policy tests cover timestamp-gap conversion and constant-time visual event dropping.

Phase 3.5 diagnostics time mixing separately from the required blocking `AudioTrack.write`. Fixed-memory histograms use 5 µs buckets through 500 µs and progressively wider buckets above that range; reported p50, p95, and p99 values are bucket upper bounds, while maxima are exact. Snapshots also distinguish intended, rendered, written, and nullable estimated-presented frames and report route changes without allocating on the per-block record path.

The release-equivalent five-minute Pixel 8a decision run is summarized in `benchmarks/2026-07-30-phase-3-and-4-summary.md`. It is the current evidence for retaining `AudioTrack`; AAudio or Oboe comparison is conditional on a reproducible approved-gate failure that remains after `AudioTrack` tuning or on unmet required device coverage.

The Phase 4.1 coordinator suite verifies that production DI exposes one application-scoped playback owner, concurrent intents execute on one control thread, and cross-mode replacement stops the old mode before starting the new one. Mode-dependent start-versus-update decisions occur on that control thread, while mismatched explicit updates are rejected. Separate tests prove valid standard retunes, polyrhythm retunes, and mute changes remain in place without teardown.

Coordinator submission returns a command sequence without waiting for engine work. Typed outcomes are replayed on a bounded control flow and retained in ownership state. Tests translate invalid input, mode mismatch, and engine exceptions into typed outcomes, preserve the last-good audible sound snapshot after preparation failure, reject stale sound publication, and verify renderer-originated, frame-correlated committed events. Typed transport callbacks are the only startup-failure path; provisional timing and legacy application observers have been removed.

The Phase 4.2 transport suite verifies immutable lifecycle transitions, same-session configuration amendments, tagged start failures and interruptions, idempotent stop, and replacement without observable `Idle`. Engine start and stop acknowledgements, sound preparation publications, and renderer records carry stale guards. Renderer records drain independently of legacy timing callbacks, flush at session teardown, and report both ring overwrite and block-buffer overflow. `Playing` requires prepared sounds plus usable opened-route and backend evidence, while slow-tempo startup does not wait for rendering.

Phase 4.3 projects metronome and polyrhythm UI state from the coordinator. ViewModel tests cover mode-specific playing state, transition-state control enablement, renderer-committed pulse and feedback events, and rapid start/stop/start submission without installing engine delegates or recording practice from start requests.

Phase 4.4 requests long-duration audio focus before opening playback, rejects denied or unavailable delayed focus as a typed start failure, and stops without automatic resume on permanent, transient, or duck-capable loss. Focus leases are released idempotently on stop, failed start, interruption, and engine release.

The dated Pixel 8a Phase 4.4 record verifies that BeatClikr took long-duration focus from a playing YouTube Music session, the competing session paused, and BeatClikr abandoned focus on stop. YouTube Music did not automatically resume; no stronger recovery claim is made.

Phase 4.5 observes both audio-device topology and the active `AudioTrack` route. `AudioTrack.ERROR_DEAD_OBJECT` maps to a terminal device-disconnected failure, and a coordinator test drives that captured backend failure through polling into `Failed`; other unsuccessful writes remain generic write failures. Focus denial and active focus loss use session-tagged start results and interruptions; the unused prerequisite state channel was removed. Route-policy tests distinguish unavailable `UNKNOWN` from usable unclassified `OTHER`, suppress initial resolution and duplicate detector signals, and convert known-to-unknown loss into `RouteUnavailable` without hiding a later known-route change. Coordinator tests reject unavailable start evidence, preserve typed usable-route changes, stop exactly once, reject stale inputs, and require explicit restart. Pixel instrumentation verifies device callback registration/removal and the production `AudioTrack` callback path through the frame session, frame engine, and metronome engine to a session-tagged interruption observer. Transport and ViewModel tests identify Bluetooth sessions as latency-variable. Dated Pixel records verify the built-in route, competing-media focus, focus release, and screen/lock teardown; wired, USB, Bluetooth, calls, noisy-device removal, and physical media-server restart were unavailable and are not claimed.

Chunk 5 Pixel UI tests verify that standard and polyrhythm screens show an accessible localized warning only while authoritative `Playing` evidence identifies Bluetooth. Typed focus, unavailable-route, stream-start, route-change, and runtime-engine diagnostics remain visible through teardown and clear on the next successful start, while secondary-output failures retain a separate presentation. Activity tests verify `FLAG_KEEP_SCREEN_ON` requires the preference, a visible Activity, and authoritative `Playing`; the flag clears for stopped, preparing, stopping, failed, interrupted, and backgrounded states and is reconstructed correctly after Activity recreation.

Chunk 7 JVM tests reject event, pulse-off, failsafe, and retry scheduling independently; verify immediate and bounded terminal torch-off attempts; retain failure while playback stays healthy; and prove later committed events still collect after scheduler failure. Process-lifecycle wiring tests define secondary effects as active at process `STARTED`, inactive at process `STOPPED`, and unchanged by Activity-only configuration, overlay, or multi-window transitions.

Phase 4.6 routes every top-level section change through the global playback stop while preserving Library and Playlist internal navigation. Explicit song selection performs one replacement start at tick zero. An application-scoped secondary-output coordinator derives haptic and torch work from session-guarded committed audio events, disables effects while the app is inactive, bounds torch pulses with a separate failsafe-off task, and publishes secondary failures without changing audio transport.

Phase 4.7 exercises serialized lifecycle races at the playback boundary. Coordinator tests cover mode replacement before start acknowledgement and after a committed event, configuration and sound commands queued behind stop, start-time route loss and engine failure, rapid replacement, repeated stop, stale callbacks, and active-play failures without duplicate teardown or session revival. On-device Hilt/Compose tests verify launch and Activity recreation never authorize playback, while recreation preserves an explicitly started session without stopping or restarting it. Foreground suppression remains covered by the process and secondary-output lifecycle observers, and the deferred background-playback clauses remain assigned to Phase 9.

Chunk 8 adds a self-instrumenting test module whose runner remains outside the target app. Its debug-only engine probe holds Preparing, Starting, Playing, Stopping, Interrupted, and Failed, records authoritative lifecycle and physical start/stop counts, and exposes no release-build component. The runner force-stops the target PID, relaunches a distinct process, and requires fresh `Idle`, zero starts, zero transitions, and a Play UI before one explicit Play creates one user-origin session. Coordinator races also deliver start, update, sound, route, and focus callbacks from a separate executor thread and assert session rejection, committed scheduling, and exact teardown counts.

The Chunk 8 process-death qualification passed on 2026-08-01 from source `475ab38311f6f506f403f7818db91faa156a1389` plus the working-tree Chunk 8 changes. Device: Pixel 8a (`akita`), Android 17 build `CP2A.260705.006` / `15641320`, one USB ADB transport. The debug probe uses built-in-route evidence without physical audio, haptic, or torch output and does not change volume, brightness, timeout, or connectivity. Command: `./gradlew --no-daemon :processdeath-test:connectedDebugAndroidTest`. Both tests passed; raw XML, protobuf, logs, device info, and HTML are under `processdeath-test/build/outputs/androidTest-results/connected/debug/` and `processdeath-test/build/reports/androidTests/connected/debug/`.

The lifecycle contracts are distinct. Rotation and Activity recreation retain application-scoped playback authority and reconstruct visible UI/keep-awake state without a restart. An active session now owns a media-playback foreground-service lifetime and continues across process background and screen-off; backgrounding never creates authority. Process death still recreates `Idle` and cannot restore authority from saved state. Secondary outputs independently follow process `STARTED`: Activity recreation and transient overlays do not suppress them, while process background or screen-off cancels pending effects and forces outputs off. Unit tests verify one service lifetime across intermediate session states and stop-only notification command semantics.

The Phase 4 clause-to-evidence register is `Docs/Validation/Phase-4-Playback-Clause-Matrix.md`. Physical observations are recorded separately in the dated Phase 4.4 focus and Phase 4.5 route/interruption records under `Docs/Validation/`.

The product owner reported that the API 31 and API 36 route/focus Android integration runs completed on 2026-08-01. This closes the cross-version integration item; no additional CI URLs or raw artifacts were supplied to the repository.

Final non-device Phase 4 qualification passed on 2026-08-02. The full debug JVM suite, `lintDebug`, and `assembleBenchmark` passed together; the permanent `PureCoreQualificationTest` then passed independently. The repository Markdown formatter and `git diff --check` completed cleanly.

Final targeted Android qualification passed on 2026-08-02 on the Pixel 8a (`akita`), Android 17, with one USB ADB transport. `InstantMetronomeViewTest`, `PlaybackDependencyGraphTest`, and `PlaybackRouteWiringTest` ran through `connectedDebugAndroidTest`: 29 tests passed with zero failures or skips. The bundle covers Hilt ownership, Activity recreation, navigation policy, typed playback diagnostics, localized Bluetooth state, keep-awake behavior, and both device-topology and active-`AudioTrack` route callbacks.

Separate JVM tests verify zero-allocation ring recording and zero-allocation every-block event rendering. Combined capture is covered functionally because HotSpot thread-allocation accounting for the inlined combination varies with compiler state; cross-thread publication visibility and torn-overwrite rejection have dedicated concurrency tests.

Phase 5 replaced legacy request counting with authoritative duration accounting. `PracticeAccountingCoordinatorTest`, repository tests, and version 4→5 migration instrumentation cover confirmed `Playing` time, the cumulative 30-second qualification threshold, repeated periods, checkpoint-time civil-day attribution, idempotent checkpoints, process recovery, and typed lifecycle-journal-gap resynchronization. Reserved metronome and polyrhythm identities remain stable while their display labels resolve from localized resources.

Phase 6 qualification covers transactional playlist ordering, safe preference decoding and bounds, typed user-facing failures, last-good sound-bank degradation, backup/restore from a version-4-shaped data set, and bounded redacted diagnostics. The JVM suite, Android repository and migration tests, lint, benchmark assembly, and the minified release path passed during the phase review.

Phase 7 adds monotonic median-based tap tempo, whole-step direct tempo selection with retained decimal display precision, authoritative playback status, transition-aware controls, localized sound labels, English/Spanish parity, accessibility semantics, effective touch-target auditing, keyboard focus, 2× font and RTL rendering, adaptive-window coverage, contrast checks, reduced-motion behavior, and differentiated screenshot assertions. Focused Pixel 8a checks for the adversarial-review corrections pass, and the full JVM suite, `lintDebug`, and `assembleBenchmark` pass together. The complete 34-test Compose class still requires one uninterrupted run on an awake, unlocked device before the Phase 7 exit gate is recorded complete.

Renderer records cross a fixed primitive ring without render-path allocation and are materialized on the coordinator control context with session, event sequence, musical role, intended frame, mute state, and explicit correlated or unavailable presentation time. Ring overwrite is observable, runtime terminal backend failures enter authoritative `Failed`, and audio-focus loss reports a tagged interruption instead of letting the engine independently authorize teardown.

Renderer regressions also cover live mute changes and a delayed first event derived from the obtained sample rate. Recovery before that delayed timeline origin remains valid and clamps its event cursor without changing output-frame ownership.

The standard-update regression changes tempo after rendering has begun and requires the replacement's first event to remain on the next old-tempo boundary. It also requires the pattern role and `EventSequence` index to continue across that boundary, proving that ramps do not restart the stream or reset musical phase.

The polyrhythm-update regression changes tempo and ratio mid-cycle, then requires the replacement to begin at the next old shared-cycle boundary with both roles coincident at cycle zero. A production-selection tripwire requires this retune branch to return before a new frame start.

These deterministic tests prove phase rules in each clock domain, not physical audio/visual coincidence. Phase 8 must measure retunes because audio selects a boundary from buffer-ahead `nextFrame`, while visual timing reaches its boundary on the monotonic wall clock; the resulting skew must be recorded rather than inferred from the configured buffer size.

The Phase 2.5 recovery suite verifies multi-event stalls, direct future-event selection, constant-time range counts, exact deadline and drop counts, coincident polyrhythm drops, repeated recovery windows, overlapping render windows, immutable origins, and stale session or mode rejection. Expired events are counted but never enumerated or returned to the renderer, preventing recovery work and catch-up output from scaling with the duration of a stall.

`PureCoreQualificationTest` is the permanent Phase 2 regression gate. It checks the twelve-hour fractional endpoint across every integer sample rate accepted by `AudioTrack`, streams twelve-hour minimum, fractional typical, maximum-density, and dense polyrhythm timelines at 44.1 and 48 kHz, injects stalls at every event position, and runs reproducible randomized standard and polyrhythm command batches. Together with the focused music-model tests, the suite contains executable coverage for MT-001 through MT-032 and TB-001 through TB-003, TB-009, and TB-010.

Run that gate independently with:

```bash
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests 'com.bfunkstudios.beatclikr.music.PureCoreQualificationTest'
```

Run only the standard-metronome characterization suite on an attached emulator:

```bash
ANDROID_SERIAL="emulator-5554" ./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.StandardMetronomeContractInstrumentedTest
```

Run only the accent characterization suite:

```bash
ANDROID_SERIAL="emulator-5554" ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AccentContractInstrumentedTest
```

Run only the polyrhythm characterization suite:

```bash
ANDROID_SERIAL="emulator-5554" ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.PolyrhythmContractInstrumentedTest
```

## Git hooks

Enable the tracked hooks once per checkout:

```bash
tools/install_git_hooks.sh
```

The pre-commit hook runs `tools/format_markdown.py` across repository Markdown. If formatting changes a file, the commit stops so the developer can review and stage the result before committing again.

## CI

GitHub Actions pins JDK 17 and required Android SDK components. Since proprietary WAV files are absent, CI generates deterministic placeholder tones from the tracked requirements.

The emulator matrix runs the bounded instrumentation and contract suites on an Android 12/API 31 Pixel 6 phone, an Android 16/API 36 Pixel 6 phone, and an API 36 Pixel C tablet. Android 17/API 37 remains a manual compatibility check until its CI emulator is stable. Long-duration stress, startup benchmark, and acoustic timing tests remain explicit physical-device or performance runs.

CI must start from a clean checkout. Success means the public source, declared toolchain, generated resources, tests, lint, debug build, and supported-version emulator checks agree. It does not mean a publishable production bundle exists.

## Production bundle checklist

Build production artifacts locally or in a private release environment with authorized acoustic assets and signing material.

1. Confirm every required WAV passes validation.
2. Run unit tests, lint, and emulator instrumentation.
3. Run the physical timing suite on the Pixel 8a reference environment.
4. Generate the release Android App Bundle.
5. Verify bundle contents, versions, package, signing, shrinker output, and acoustic resources.
6. Review Play Console device and Android-version distribution before release, and record any remaining pre-Android 12 users who will no longer receive updates.
7. Distribute through an internal Play track and perform a smoke test.
8. Record the commit, toolchains, device result, and artifact checksum.

Never commit signing keys, credentials, private audio, or private manifests.

## Benchmark records

The repeatable Phase 8 device and acoustic workflow is defined in [`Validation/Phase-8-Qualification-Protocol.md`](Validation/Phase-8-Qualification-Protocol.md). Remaining product-owner decisions and physical observations are listed in [`Validation/Phase-8-Physical-Handoff.md`](Validation/Phase-8-Physical-Handoff.md).

Each record should state:

- date and Git commit;
- device or emulator model;
- Android version and build;
- build variant and audio route;
- exact test and duration;
- callback error, underruns, drift, and any acoustic measurement;
- limitations or environmental changes.

Keep separate emulator-correctness and physical-audio baselines. Create a new record when the toolchain, OS, device, or timing design changes.
