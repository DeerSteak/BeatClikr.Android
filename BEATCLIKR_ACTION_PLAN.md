# BeatClikr Android — Prioritized Remediation Plan

**Created:** 2026-07-27  
**Rewritten after contract approval:** 2026-07-28  
**Sources:** `ADVERSARIAL_PROJECT_REVIEW.md`, accepted Phase 1 ADRs, current Android implementation audit, and sibling iOS behavior  
**Objective:** make BeatClikr a demonstrably reliable, accessible, privacy-respecting, best-in-class free Android metronome.

## How to execute this plan

Work in phase order. Do not begin product-expansion work while a P0 gate is open. Each phase ends with a measurable exit gate; completing code without its verification does not complete the phase.

Keep changes reviewable:

- [ ] one architectural decision or cohesive behavior per pull request;
- [ ] tests in the same pull request as behavior;
- [ ] benchmark results attached to timing-related pull requests;
- [ ] migrations tested with production-like fixtures;
- [ ] documentation updated whenever a product contract changes.

## Priority summary

| Order | Phase | Outcome | Blocks release |
|---:|---|---|:---:|
| 0 | Reproducible foundation | Authorized clean builds and truthful documentation | Yes |
| 1 | Timing contract | Measurable musical and playback semantics | Yes |
| 2 | Executable musical core | Approved musical rules become deterministic tests and frame events | Yes |
| 3 | Frame-addressed audio | Events render at exact offsets with prepared immutable sounds | Yes |
| 4 | Authoritative Android playback | One coordinator owns sessions, lifecycle, routes, services, and outputs | Yes |
| 5 | Trustworthy practice history | Confirmed duration, civil-day accounting, and version 4 migration | Yes |
| 6 | Application integrity | Transactional data, safe preferences, privacy, failures, and diagnostics | Yes |
| 7 | Product UI quality | Contract-aware controls, accessibility, localization, and adaptive layouts | Yes |
| 8 | Release qualification | Approved budgets proven on the Pixel 8a and every claimed route | Yes |
| 9 | Extended iOS parity | Background/locked playback and optional coexistence delivered without weakening the core | No |
| 10 | Architecture protection | Boundaries and automated regression gates prevent backsliding | No |
| 11 | Product differentiation | New musician value built only on the trusted core | No |

## Phase 0 — Establish a reproducible foundation

**Purpose:** ensure every authorized developer and release worker can build the same app before changing its core.

**Status (2026-07-28): complete.** Toolchain, proprietary asset handling, documentation, clean release verification, emulator and Pixel correctness, acoustic timing, startup latency, sustained resource use, and unplugged battery behavior are recorded. Observed timing and underrun gaps remain assigned to their implementation phases rather than blocking the reproducible baseline.

### 0.1 Fix the local and CI toolchain

- [x] Install/configure a supported JDK 17 and record the expected environment.
- [x] Add a CI workflow for:
  - [x] `:app:assembleDebug`;
  - [x] `:app:bundleRelease` using non-production signing in CI;
  - [x] `test`;
  - [x] `lint`;
  - [x] migration instrumentation tests where an emulator is available.
- [x] Pin the JDK and Android SDK components.
- [x] Set Android 12/API 31 as the supported floor and exercise API 31 and the API 36 target in CI.
- [x] Add a concise developer setup section to the README.

### 0.2 Formalize proprietary audio provisioning

**Status (2026-07-28): complete.** For now, production WAVs and their schema-2 manifest remain local to authorized release machines, while public CI uses generated non-production tones. Access-controlled CI provisioning is not currently required.

- [x] Keep `app/src/main/res/raw/*.wav` ignored and keep `.gitkeep` tracked.
- [x] Create a checked-in validator that expects the exact 30 `SoundFile` resource names.
- [x] Maintain a private manifest with SHA-256, asset version, sample rate, channel count, sample width, provenance, and license.
- [x] Add explicit bank, PCM encoding, peak, and leading-silence metadata to the private manifest.
- [x] Define the current policy: provision proprietary audio on authorized local release machines; do not require access-controlled CI provisioning for now.
- [x] Make missing assets fail early with one clear setup error.
- [x] Decide whether public CI uses validated non-production test tones or skips Android resource builds explicitly.
- [x] Document that `silence_d7.wav` is unused, then either remove it locally or give it a defined role.

### 0.3 Correct documentation

**Status (2026-07-28): complete.** The README is current and indexes topic docs for architecture, models, ViewModels, views, services, constants, library/data, capabilities, playback performance, validation, and benchmarks.

- [x] Remove SoundPool descriptions; the active backend is `AudioTrack`.
- [x] Remove the claim that proprietary WAVs are tracked.
- [x] Replace stale roadmap items that already exist.
- [x] Remove unverified “sample-accurate,” “<5 ms jitter,” and “no drift” claims.
- [x] Link to the timing contract, benchmarks, and known route limitations when they exist.

### 0.4 Capture a baseline

**Status (2026-07-28): complete.** Emulator and Pixel 8a engine-correctness baselines are recorded. Acoustic loopback produced a two-minute built-in-speaker recording with no missing events, fitted tempo of 240.004918 BPM, and −2.535848 ms fitted drift. Longer and multi-tempo acoustic qualification is deferred to Phase 8. Startup latency is documented for 30 cold and 30 warm starts. A lower-overhead 30-minute profile completed with zero underruns and average CPU use of 20.94% of one core. The unplugged battery observation met the provisional consumption budget without thermal escalation, while its one-hour audio workload exposed four underruns for Phases 2 and 3 to address.

- [x] Build a baseline APK.
- [x] Record the emulator engine-correctness baseline.
- [x] Record the Pixel 8a engine-correctness baseline on Android 17.
- [x] Record a two-minute maximum-density Pixel 8a acoustic baseline.
- [x] Complete a 30-minute maximum-density Pixel 8a stress run.
- [x] Capture underruns under normal and intrusive diagnostic loads.
- [x] Capture startup latency.
- [x] Capture CPU, memory, and thermal behavior.
- [x] Repeat CPU, memory, thermal, and underrun measurement with a lower-overhead profiler.
- [x] Capture battery consumption without external power.
- [x] Save results as a versioned benchmark artifact, not as an unsupported README claim.

**Exit gate**

- [x] An authorized clean environment provisions assets and passes build, unit-test, lint, and release-bundle jobs.
- [x] README setup instructions reproduce that result.
- [x] All 30 sound mappings pass automated name/format/checksum validation.
- [x] The versioned baseline includes startup latency, lower-overhead resource profiling, and unplugged battery consumption.

## Phase 1 — Define the product and timing contract

**Purpose:** decide what correctness means before implementing another timing engine.

**Status (2026-07-28): complete.** The product owner approved the musical-time, playback-lifecycle, practice-history, and quantitative budget contracts after the iOS parity review and current Android implementation audit. Later implementation and acceptance tests must cite these approved clause IDs.

### 1.1 Write timing ADRs

Specify:

- [x] BPM unit for every groove and odd meter;
- [x] exact accent and subdivision semantics;
- [x] tempo-ramp choices, counting, boundaries, and stop behavior;
- [x] tempo range and precision;
- [x] start and count-in behavior;
- [x] effective boundary for tempo, subdivision, pattern, and sound changes;
- [x] phase behavior after a late callback, interruption, and route change;
- [x] whether missed events are dropped or phase is restarted;
- [x] polyrhythm cycle and index semantics;
- [x] expected behavior on speaker, wired, USB, and Bluetooth routes;
- [x] audio/visual/haptic/flash alignment policy;
- [x] foreground-only versus background/lock-screen playback.
- [x] single-active-mode and navigation stop/continuity behavior;
- [x] Android long-duration audio-focus ownership and iOS mixing divergence.

### 1.2 Define measurable budgets

Set initial acceptance budgets for:

- [x] long-run scheduler drift;
- [x] p50/p95/p99 inter-onset error;
- [x] missed/doubled events;
- [x] startup latency;
- [x] underruns;
- [x] tempo-change boundary error;
- [x] CPU and battery consumption.

- [x] Use separate budgets for low-latency local routes and Bluetooth without promising deterministic Bluetooth latency.

### 1.3 Define practice semantics

- [x] Decide when a start becomes a recorded practice session.
- [x] Define minimum meaningful duration.
- [x] Define local-day, timezone-change, travel, and daylight-saving behavior.
- [x] Decide whether repeated song plays increment count, duration, or both.

**Exit gate**

- [x] ADRs and budgets are approved, and every planned scheduler/playback acceptance behavior maps to a contract clause.
- [x] No intended timing behavior remains dependent on an undocumented implementation accident.

## Phase 2 — Make the musical contract executable

**Purpose:** preserve Android's already-correct musical behavior while replacing implicit polling state with a pure, deterministic sample-frame timeline.

**Dependency rule:** do not alter the production audio path until the current iOS-parity behaviors and approved restart/recovery rules pass against the new domain model.

### 2.1 Capture parity fixtures before extraction

Complete each subsection as a separate pull request. These PRs add evidence and reusable fixtures without changing production playback behavior.

#### 2.1a Pin the matched comparator

- [x] Record the exact comparator commit, release variant, Pixel 8a build, route, settings, and measurement commands for TB-018.
- [x] Capture the current release-build startup and underrun reference before Phase 3 changes production timing.
- [x] Capture CPU, memory, thermal, and battery reference runs using the documented low-overhead protocol.
- [x] Store raw artifacts and a concise benchmark summary.
- [x] Make no scheduler, renderer, or product-behavior changes in this PR.

#### 2.1b Characterize standard metronome behavior

- [x] Add the contract-ID test naming or metadata convention.
- [x] Cover the 30–240 BPM bounds and decimal scheduling inputs.
- [x] Cover quarter, eighth, triplet, and sixteenth subdivision counts.
- [x] Cover tick-zero starts, beat/rhythm sound roles, mute event continuity, stop/reset, and no count-in.
- [x] Port the corresponding representative iOS fixtures.
- [x] Keep fixture data independent of Android resource IDs.

#### 2.1c Characterize accents and alternate sixteenths

- [x] Cover every odd-meter pattern and both odd-quarter and odd-eighth timing units.
- [x] Assert the first pattern step and every additive-group boundary are accented.
- [x] Assert accented steps use the beat sound and unaccented steps use the rhythm sound.
- [x] Cover alternate-sixteenth beat sounds on ticks zero and two while feedback remains tick-zero-only.
- [x] Port the corresponding iOS fixtures without changing the Android pattern definitions.

#### 2.1d Characterize polyrhythm behavior

- [x] Cover displayed `M:N` meaning and cycle duration.
- [x] Cover both values from 1 through 15, including equal, coprime, and shared-factor ratios.
- [x] Assert a shared cycle origin and exact coincident-event identity.
- [x] Verify beat/rhythm indices across complete cycles.
- [x] Keep the fixture and expected-event representation reusable by the future pure scheduler.

#### 2.1e Characterize instant tempo ramp

- [x] Cover the approved increment and interval choices.
- [x] Cover initial-beat counter behavior and subdivision exclusion.
- [x] Cover odd-meter accent counting.
- [x] Cover the 240 BPM cap and restoration of the captured starting BPM on stop.
- [x] Assert ramp is unavailable for song, playlist, and polyrhythm playback.
- [x] Port the corresponding iOS fixtures.

**2.1 exit gate**

- [x] Each characterization area is independently reviewed and merged.
- [x] Every Android musical behavior marked conforming in the audit has a contract-tagged test or an explicit Phase 2 replacement test.
- [x] No 2.1 pull request changes audible production behavior.

### 2.2 Introduce Android-free musical models

#### 2.2a Introduce the standard model and event vocabulary

- [x] Define an immutable standard-metronome configuration.
- [x] Represent BPM as an exact normalized rational input instead of repeated floating conversion.
- [x] Define an event containing intended frame, role, sound role, beat/accent identity, and cycle position.
- [x] Keep Android classes, clocks, handlers, audio objects, persistence, and resource IDs out of the musical package.
- [x] Preserve the approved 30–240 BPM range, subdivision mapping, and additive accents.

#### 2.2b Complete the shared session model

- [x] Define an immutable polyrhythm configuration preserving the approved `M:N` meaning.
- [x] Define a session origin and monotonically increasing event sequence.
- [x] Extend the shared event vocabulary only if the polyrhythm model exposes a missing invariant.
- [x] Keep the completed model package Android-free.

### 2.3 Implement the pure sample-frame timeline

#### 2.3a Implement exact standard-metronome events

- [x] Generate standard events intersecting a requested absolute frame range.
- [x] Derive independently rounded frames from exact rational periods so awkward values cannot accumulate drift.
- [x] Start every standard session at tick zero with a monotonically increasing event sequence.
- [x] Make mute an output attribute that does not remove events or phase.
- [x] Preserve regular subdivisions, additive accents, alternate-sixteenth sound roles, and adjacent-range boundaries.

#### 2.3b Complete polyrhythm and ramp timelines

- [x] Represent coincident polyrhythm voices at one exact frame.
- [x] Start polyrhythm at a shared origin and preserve complete-cycle indices.
- [x] Reset standard and polyrhythm phase through a new session origin.
- [x] Implement instant tempo ramp as deterministic musical state.

### 2.4 Implement approved command boundaries

- [x] Define typed start, stop, tempo, groove, pattern, sound, bank, mute, and polyrhythm commands.
- [x] Give every command a session ID, command sequence, immutable payload, and submission timestamp.
- [x] Restart standard tempo, groove, pattern, and ramp changes at tick zero.
- [x] Restart polyrhythm tempo and ratio changes at a shared cycle origin.
- [x] Prepare sound changes separately and publish them only at the approved restart boundary.
- [x] Apply same-boundary commands in sequence and expose the final configuration atomically.
- [x] Keep logical playback identity stable across configuration restarts for later practice accounting.

### 2.5 Implement deadline recovery and diagnostics

- [x] Detect every expired event relative to the render window.
- [x] Drop expired events without emitting a catch-up burst.
- [x] Advance directly to the first future event derived from the session origin.
- [x] Count deadline misses and dropped events by session and mode.
- [x] Prove that recovery cannot duplicate a frame or move the origin.

### 2.6 Exhaust the pure core

- [x] Run exact fixtures at every supported sample rate used by the backend.
- [x] Simulate at least 12 hours at minimum, typical, and maximum density.
- [x] Add randomized command-sequence and invariant tests.
- [x] Add multi-event stalls at every event position.
- [x] Assert TB-001 through TB-003, MT-001 through MT-032, and all relevant TB-009/TB-010 boundaries.

**Exit gate**

- [x] The pure core passes every musical clause without Android dependencies.
- [x] Twelve-hour simulations meet TB-001 through TB-003.
- [x] Approved configuration boundaries and recovery behavior are executable tests.
- [x] Production playback remains unchanged until Phase 3 replaces its timing source in one controlled integration.

## Phase 3 — Render frame events instead of queued clicks

**Purpose:** connect the deterministic timeline to real audio without allowing Android callback timing to become musical time.

### 3.0 Protect the Phase 2 boundary before integration

- [x] Add a lightweight architecture test that fails if the `music` package imports Android APIs, and run it with the ordinary unit-test suite.
- [x] Define the integration rule that domain `require()` failures must be translated at the package boundary into typed, recoverable failures before control reaches the renderer, audio thread, coordinator, or UI.
- [x] Add boundary tests proving invalid external commands and configurations cannot escape as uncaught domain exceptions on the render path.

### 3.1 Define a narrow render contract

- [x] Create a backend-neutral interface that opens, starts, renders, stops, timestamps, and reports failures.
- [x] Make the renderer request events for each output frame range.
- [x] Mix each waveform at its exact offset inside the render block.
- [x] Carry waveform tails across blocks.
- [x] Define and test saturating mix behavior for coincident and overlapping sounds.
- [x] Keep allocation, locks, logging, file I/O, database work, and UI callbacks outside the real-time path.

### 3.2 Build immutable prepared sound banks

- [x] Decode and validate WAV resources off the render thread.
- [x] Normalize channel layout and sample rate before publication.
- [x] Preserve approved leading silence rather than trimming proprietary assets implicitly.
- [x] Store immutable beat/rhythm waveforms keyed by sound bank and sound identity.
- [x] Publish a complete replacement bank atomically.
- [x] Remove duplicate cache preparation and recursive render-time loading.
- [x] Return a typed failure for missing, corrupt, empty, or incompatible required sounds.
- [x] Test cache versioning, corruption, bank switching, and concurrent preparation.

### 3.3 Replace the current `AudioTrack` queue safely

- [x] Implement frame-offset rendering with `AudioTrack` first so backend replacement and scheduler correctness are separate risks.
- [x] Remove the one-millisecond metronome polling loop.
- [x] Remove pending-click enqueue time as an event-position mechanism.
- [x] Use obtained sample rate, channel count, burst size, buffer size, and performance mode as runtime facts.
- [x] Define and test the mono-renderer to obtained-channel-layout boundary so stereo output cannot halve duration or misplace frames.
- [x] Correlate intended and written frames with `AudioTimestamp` where available.
- [x] Bind immutable prepared waveforms once at session or stream publication; never call `copySamples()` per render block.
- [x] Make the stream owner reset `FramePcmRenderer` on a new session origin, stop/restart, stall recovery, underrun re-render, or any noncontiguous output range.
- [x] Compose `DeadlineRecovery` with renderer reset and next-frame ownership so a discontinuity cannot retain stale waveform tails.
- [x] Keep stop and stream teardown bounded and idempotent.

### 3.4 Evaluate AAudio or Oboe only with evidence

**Decision (2026-07-30): retain `AudioTrack`.** The release-equivalent five-minute Pixel 8a run completed 59,976 blocks with zero underruns, deadline misses, or dropped events. Mix p99 was at most 0.225 ms against the obtained 5 ms burst period, so an AAudio/Oboe comparison has no current evidence-based trigger.

- [x] Define the backend-comparison trigger after the frame renderer works.
- [x] Measure underruns, mix cost, blocking-write behavior, stability, and obtained stream facts on the Pixel 8a.
- [x] Require a material approved-budget or reliability improvement before adopting Oboe/AAudio.
- [x] Retain the simpler `AudioTrack` implementation while it meets the measured gates.
- [x] Reconsider a comparison only after a reproducible gate failure survives `AudioTrack` tuning, or required device coverage cannot be met.

### 3.5 Instrument the production path

- [x] Record intended, rendered, written, and estimated-presented frames.
- [x] Record mix and blocking-write duration percentiles, deadline misses, dropped events, and underruns.
- [x] Record obtained backend, route and route changes, sample rate, burst, buffer, and performance properties.
- [x] Buffer diagnostics in memory and export them off the render thread.
- [x] Bound diagnostic memory and verify that enabling diagnostics does not create underruns.

**Exit gate**

- [x] Events begin at exact frame offsets inside render blocks.
- [x] The Handler polling scheduler and cross-thread click queue no longer own event timing.
- [x] Sound failures are explicit and no empty waveform can masquerade as successful playback.
- [x] Render-path tests prove no allocation or blocking work in the mixer section.

## Phase 4 — Make Android playback authoritative

**Purpose:** implement the core approved playback contract as one vertical subsystem. Extended background and lock-screen parity builds on this owner in Phase 9 rather than delaying timing and foreground correctness.

### 4.1 Establish one playback owner and command boundary

**Story purpose:** introduce one application-scoped control-plane owner, route every production playback command through it, and prove that standard and polyrhythm sessions cannot compete for the engine. This story establishes ownership and serialization; it does not implement the final transport state machine, migrate all UI observers, or add audio-focus and route policy.

- [x] Create an application-scoped `PlaybackCoordinator` and make the concrete audio engine private to that boundary.
- [x] Define feature intents for start, stop, mode replacement, configuration, sound selection, and mute instead of exposing mutable engine properties.
- [x] Serialize intents on one control context separate from the render callback.
- [x] Stop the old mode before publishing a replacement standard or polyrhythm session.
- [x] Apply standard configuration, polyrhythm configuration, and mute changes in place; preserve the stream, waveform tails, and musical phase through the approved continuation boundary.
- [x] Reject stale or superseded work at the coordinator boundary so it cannot mutate the active engine session.
- [x] Translate invalid inputs, rejected publications, and engine failures into typed coordinator outcomes; never allow raw domain exceptions onto the control, render, or audio thread.
- [x] Track requested sound configuration separately from the last successfully prepared and audible snapshot.
- [x] Expose coordinator-owned observation seams for ownership and provisional timing callbacks without mislabeling wall-clock callbacks as audio-committed events.
- [x] Test concurrent intents, rapid mode replacement, preparation failure, stale completion, and requested-versus-audible sound state.

**4.1 story gate**

- [x] Production playback entry points cannot start or mutate the engine except through `PlaybackCoordinator`.
- [x] At most one engine session and one active mode exist after every serialized command sequence.
- [x] The coordinator can be integrated without claiming final transport truth before 4.2.

### 4.2 Make transport state authoritative and observable

**Story purpose:** turn the 4.1 owner into the single source of truth for session lifecycle. Every observer receives the same committed transition and stale asynchronous work cannot revive or overwrite a superseded session. Audio focus and route policy enter through session-tagged start results and interruptions so there is no parallel prerequisite state owner.

Use explicit states such as:

```text
Idle → Preparing → Starting → Playing → Stopping → Idle
                   ↘ Failed(reason)
Playing → Interrupted(reason) → Idle
Playing(old) → Stopping(old) → Preparing(new) → Starting(new) → Playing(new)
                  replacement transaction; do not publish Idle
```

- [x] Define immutable `Idle`, `Preparing`, `Starting`, `Playing`, `Stopping`, `Interrupted`, and `Failed` states with legal transitions.
- [x] Include session ID, mode, committed configuration, audible sound snapshot, route, backend, start origin, and failure or interruption reason where applicable.
- [x] Enter `Playing` after sounds are prepared, the stream is running, and the first event is scheduled at an authoritative frame; do not wait for a slow-tempo event to be rendered or presented.
- [x] Keep first-event render and presentation commitment as observable event milestones without using either to delay transport state.
- [x] Represent audio-focus denial and unavailable routes as typed start failures, and active focus or route loss as session-tagged interruptions.
- [x] Make start and stop idempotent and make repeated stop converge on `Idle`.
- [x] Execute mode replacement as one intent: publish `Stopping(old) → Preparing(new) → Starting(new) → Playing(new)` without an intermediate observable `Idle` or a second user tap.
- [x] Reject stale commands, preparation completions, engine callbacks, failures, and stop acknowledgements from superseded session IDs.
- [x] Publish one replayable state stream and one ordered committed-event stream for UI, service, practice, and diagnostics consumers.
- [x] Emit bounded per-event records off the render thread with session, sequence, role, intended frame, and correlated presentation time; counters alone are not an event stream.
- [x] Derive committed-event presentation time from `AudioFrameCorrelation`, represent unavailable correlation explicitly, and do not substitute configured buffer depth as measured latency.
- [x] Publish engine stops and failures through the authoritative state rather than leaving observers to infer them.
- [x] Fold `MetronomeAudioEngine` transport decisions into coordinator state; retain only private mechanical flags that cannot independently authorize playback or contradict the coordinator.
- [x] Keep engine-internal underrun/discontinuity recovery inside `Playing` and expose it through diagnostics without creating a new session.
- [x] Never automatically resume a session after interruption, route loss, engine failure, or user stop; those transitions require explicit restart.
- [x] Make the coordinator own the stop/replacement decision and the engine port own bounded physical stream teardown; normal stop closes the backend while reusable control/render threads may remain alive.
- [x] Keep warm-stream retention out of the transport contract unless Phase 8 startup evidence justifies it; prewarming remains explicit and cannot authorize playback.
- [x] Test every legal transition, illegal transition, stale callback, repeated command, failure edge, and observer ordering guarantee without Android dependencies.

**4.2 story gate**

- [x] Coordinator state is the sole transport truth and contains enough committed context for 4.3 ViewModels to become projections.
- [x] All observers see the same ordered lifecycle, and no stale asynchronous result can change the current session.
- [x] Audio-focus and route integrations can stop or fail a session through typed inputs without adding a second state owner.
- [x] Slow-tempo startup, mode replacement, and normal stop have explicit state traces with no false `Playing`, observable `Idle` flicker, or retained open backend.

### 4.3 Move ViewModels onto coordinator state

- [x] Remove independent metronome and polyrhythm `isPlaying` truth.
- [x] Remove ViewModel delegate installation and cleanup.
- [x] Derive buttons, labels, animation, and enabled controls from coordinator state.
- [x] Keep draft/UI selection state local while active playback configuration remains authoritative.
- [x] Make rapid start/stop/start and mode switching require one tap per user intent.
- [x] Do not record practice directly from ViewModel start requests.

### 4.4 Implement long-duration audio-focus ownership

- [x] Request `AUDIOFOCUS_GAIN` before committing playback.
- [x] Treat focus denial or an unavailable delayed grant as a typed start failure.
- [x] Stop on permanent, transient, and duck-capable focus loss rather than automatically resuming.
- [x] Pair every successful focus acquisition with abandon whenever the last mode stops or fails.
- [x] Verify another media application pauses or ducks according to Android policy and can recover after BeatClikr abandons focus.
- [x] Document that backing-track coexistence is not guaranteed and is secondary to metronome focus ownership.

### 4.5 Implement routes and interruption handling

- [x] Observe audio-device additions, removals, and active-route changes.
- [x] Stop the active session, record a typed reason, release the old stream, and require explicit restart.
- [x] Rebuild using the obtained properties of the new route.
- [x] Identify Bluetooth routes and expose latency-variable state to UI.
- [x] Test speaker, USB, wired when available, Bluetooth, media-server loss, calls, and noisy-device removal. Built-in evidence is retained; the product owner completed and accepted the remaining device matrix on 2026-08-02.

### 4.6 Enforce navigation and secondary-output policy

- [x] Route every top-level navigation change through a global playback stop.
- [x] Stop the hidden mode when switching instant metronome and polyrhythm.
- [x] Preserve song playback through Library/Playlist detail, editors, pickers, sheets, and focus mode.
- [x] Replace the active song at tick zero when the user explicitly plays another Library, Playlist, or focus-mode song.
- [x] Derive visual, haptic, and flash events from committed audio events.
- [x] Stop haptic and flash effects whenever the visible app becomes inactive.
- [x] Give torch pulses a bounded duration and independent failsafe off.
- [x] Guarantee torch off on stop, interruption, exception, and lifecycle exit.
- [x] Surface secondary-output failure without disturbing healthy audio.

### 4.7 Test session and lifecycle races

- [x] Rapid start/stop/start and repeated stop.
- [x] Mode change during preparation and after the first event.
- [x] Configuration and sound changes racing stop.
- [x] Rotation, Activity recreation, foreground process transitions, and explicit stop.
- [x] Route removal and engine failure during start and play.
- [x] Stale callback after a replacement session.
- [x] Process recreation with no unauthorized automatic playback.

**Exit gate**

- [x] One coordinator is the only authority for playback.
- [x] UI, service, practice observers, and engine state cannot disagree.
- [x] Every non-deferred playback clause passes; PL-007, PL-011, PL-012, and the background portion of PL-025 remain explicitly tracked in Phase 9.
- [x] Race tests produce no stale click, duplicate session, leaked focus, or two-tap recovery.

## Phase 5 — Make practice history truthful

**Purpose:** replace immediate launch counting with confirmed, cumulative, civil-day-aware practice accounting while preserving all version 4 user data.

**Status (2026-08-02): complete — 36 of 36 checklist items complete.** Version 5 now has authoritative lifecycle accounting, periodic idempotent checkpoints, recoverable typed lifecycle-gap resynchronization, civil-time attribution, deterministic duration display, localized reserved-item labels, qualified-history filtering, and production-shaped on-device migration and recovery coverage. JVM unit tests, Android repository and migration tests on the Pixel 8a, lint, benchmark assembly, minified release assembly, and the CI Android-test Hilt compilation path pass.

### 5.1 Design the version 5 history schema

- [x] Store stable local civil-date identity separately from an instant timestamp.
- [x] Store timezone/offset and calendar metadata required by PH-014 through PH-018.
- [x] Store accumulated duration with subsecond precision and playback-period count.
- [x] Preserve stable song IDs and reserved metronome/polyrhythm IDs, resolving reserved display labels from localized resources.
- [x] Add unique constraints that permit one aggregate per item and civil day.
- [x] Define only the active checkpoint state needed for process-death recovery without double counting.

### 5.2 Implement coordinator-driven accounting

- [x] Begin a period only from a confirmed `Playing` transition and committed first event.
- [x] End or checkpoint on every transition out of `Playing`.
- [x] Use an explicitly named shared monotonic clock domain for duration.
- [x] Make duplicate start notification for the same session idempotent.
- [x] Close the prior item before beginning a different item.
- [x] Keep logical accounting open across approved configuration restarts.
- [x] Accumulate short periods while hiding the item until 30 seconds.
- [x] Increment both duration and period count for repeated song, metronome, and polyrhythm plays.
- [x] Exclude preparation, failures, interruptions, count-in, and stopped time.

### 5.3 Implement civil-time boundaries

- [x] At each checkpoint, select the stable Gregorian local-day record using the device timezone at checkpoint time.
- [x] Assign the entire monotonic interval since the prior checkpoint to that selected record, matching iOS rather than splitting at an exact boundary.
- [x] Store the record's local day key, timezone identifier, calendar identifier, and original absolute creation timestamp.
- [x] Let a timezone or UTC-offset change affect the next checkpoint's selected record without stopping playback.
- [x] Preserve already stored civil-day identities after travel.
- [x] Handle DST repeated and skipped times without altering monotonic elapsed duration.
- [x] Test checkpoint attribution across midnight and timezone changes without actually waiting for wall time.

### 5.4 Make updates transactional and recoverable

- [x] Put day creation, aggregate creation, duration update, period update, and checkpoint update in Room transactions.
- [x] Replace repository read-modify-write races with atomic DAO operations.
- [x] Make retries idempotent under process death and coroutine cancellation, and resynchronize lifecycle journal gaps without stopping collection.
- [x] Derive streaks and reminders only from qualified aggregates.
- [x] Define deterministic user-facing duration rounding.

### 5.5 Migrate version 4

- [x] Export the version 5 schema.
- [x] Create a real 4→5 migration; versions 1–3 remain unsupported pre-release schemas.
- [x] Give each legacy history row 30 seconds and preserve its current count and identity.
- [x] Preserve songs, playlists, entries, preferences, and relationships unchanged.
- [x] Add version 4 production-shaped migration fixtures for songs, playlists, standard history, metronome, and polyrhythm.
- [x] Test upgrade, interrupted-open recovery, backup restore, and downgrade policy.

**Exit gate**

- [x] PH-001 through PH-021 pass repository, coordinator, migration, and civil-time tests.
- [x] No failed or sub-threshold unqualified start appears in history or streaks.
- [x] Every version 4 fixture retains all user-authored data and previously earned history.

## Phase 6 — Harden application data, failures, privacy, and diagnostics

**Purpose:** close non-audio integrity gaps before final product and hardware qualification.

**Status (2026-08-02): complete — 30 of 30 checklist items complete.** Transactional playlist ordering, safe versioned persistence codecs, bounded localized preferences, typed failure recovery, sound-bank degradation visibility, backup/privacy policy, and bounded redacted local diagnostics are implemented and tested.

### 6.1 Make playlist mutations transactional

- [x] Move add-and-sequence allocation into one Room transaction.
- [x] Move delete-and-resequence into one Room transaction.
- [x] Enforce or safely maintain unique ordering per playlist.
- [x] Resolve concurrent adds, deletes, and reorders deterministically.
- [x] Test cancellation and deletion of songs referenced by playlists.

### 6.2 Make preferences safe

- [x] Replace unsafe enum `valueOf` and non-null assertions with versioned codecs and defaults.
- [x] Clamp BPM, ratios, ramp values, and other numerics at the persistence boundary.
- [x] Preserve historical keys and renamed enum values with explicit migrations.
- [x] Test corrupt, unknown, legacy, and restored preference values.
- [x] Move to DataStore only if it simplifies tested invariants; do not make conversion a goal by itself.

### 6.3 Define typed failures and recovery

- [x] Define failures for asset validation, decode/cache, stream creation, route, database, reminder, haptic, and torch operations.
- [x] Separate retryable, user-actionable, degraded, and fatal failures.
- [x] Map each user-actionable failure to concise UI and a safe recovery action.
- [x] Never replace a corrupt required sound with silence while reporting success.
- [x] Surface sound-preparation failure and last-good-bank degradation instead of leaving requested and audible sound state divergent.
- [x] Keep retry/backoff outside real-time and transaction-critical paths.

### 6.4 Make backup and privacy intentional

- [x] Classify songs, playlists, practice history, preferences, diagnostics, proprietary resources, and generated PCM.
- [x] Exclude regenerable PCM and transient diagnostics from backup.
- [x] Preserve user-authored data according to the documented restore policy.
- [x] Test backup and restore from a version 4-shaped data set into the current schema.
- [x] Publish a concise privacy statement covering offline behavior and absence of tracking.

### 6.5 Add privacy-safe local diagnostics

- [x] Add a copy/share diagnostics action.
- [x] Include app/build, device/OS, route/backend/stream properties, latency confidence, underruns, drops, deadline misses, and recent session transitions.
- [x] Exclude song names, playlist names, practice details, file paths, and other user content by default.
- [x] Bound retained events and redact typed failures before export.
- [x] Test output shape and redaction.

**Exit gate**

- [x] Concurrent playlist and history operations preserve invariants.
- [x] Malformed preferences cannot crash startup.
- [x] Backup contains no generated cache or diagnostics and restore preserves user data.
- [x] Core failures are visible, recoverable where possible, and never falsely reported as success.

## Phase 7 — Finish the product-facing experience

**Purpose:** expose the approved behavior clearly and make the app usable across accessibility, language, and layout conditions before final timing qualification.

**Status (2026-08-02): complete — 34 of 34 checklist items complete.** Musical precision, tap tempo, authoritative playback presentation, accessibility semantics, reduced motion, adaptive layouts, contrast, localization, pseudolocales, and CI UI qualification are implemented and tested.

### 7.1 Reconcile controls with the musical contract

- [x] Make direct slider selection move in whole BPM steps while retaining valid imported decimal BPM.
- [x] Display stored decimal BPM with up to two fractional digits without noisy trailing zeros.
- [x] Disable or explain controls that cannot apply during the current mode.
- [x] Show authoritative preparing, playing, interrupted, and failed states.
- [x] Show Bluetooth latency-variable status without implying deterministic correction.
- [x] Preserve iOS terminology and behavior where Android conventions do not require a difference.

### 7.2 Harden tap tempo

- [x] Use `elapsedRealtimeNanos`.
- [x] Keep the approved range and whole-BPM visible result.
- [x] Reset after the defined inactivity interval.
- [x] Reject impossible double taps and stale intervals.
- [x] Use an explicitly selected median, trimmed-mean, or weighted estimator.
- [x] Add lightweight confidence/reset feedback.
- [x] Test accidental double taps, tempo jumps, sparse taps, and wall-clock changes.

### 7.3 Complete accessibility semantics

- [x] Audit every icon-only, plus/minus, segmented, slider, transport, and dismiss control.
- [x] Put label, role, state, and action semantics on the clickable parent.
- [x] Verify minimum touch targets.
- [x] Announce transport and failure state without announcing every beat.
- [x] Support TalkBack, keyboard, switch access, and external focus traversal.
- [x] Respect reduced-motion preferences for beat animation.
- [x] Avoid color-only beat, accent, and status distinctions.

### 7.4 Verify visual and adaptive behavior

- [x] Meet contrast requirements across light, dark, and high-contrast conditions.
- [x] Support font scale 2.0 without clipped or unreachable controls.
- [x] Test compact phone portrait and landscape.
- [x] Test tablet, foldable-sized window, split screen, and freeform resizing with emulators where physical hardware is unavailable.
- [x] Preserve transport access, one-handed use, and large targets during performance.
- [x] Add screenshot tests for critical layouts, themes, and states.

### 7.5 Complete localization

- [x] Replace enum-owned English display strings with resource-backed stable IDs.
- [x] Remove remaining user-facing Kotlin literals.
- [x] Complete Spanish coverage for every current feature and failure.
- [x] Test pseudolocale, long strings, plurals, numbers, and RTL.
- [x] Keep diagnostic identifiers and contract IDs untranslated.

**Exit gate**

- [x] The UI faithfully represents authoritative state and approved musical precision.
- [x] TalkBack, 2× font, reduced motion, RTL, keyboard/switch access, compact, and expanded layout checklists pass.
- [x] Automated semantics, screenshot, and pseudolocale checks run in CI.

## Phase 8 — Qualify the release on real hardware

**Purpose:** prove the completed system rather than using partial architecture or debug callback measurements as product claims.

**Required hardware:** the Pixel 8a on Android 17 is the reference physical device. Lack of a broader personal device collection does not block this phase. Additional devices are opportunistic evidence or a prerequisite only for claims that generalize beyond the reference device.

### 8.1 Finalize the repeatable harness

- [x] Correlate intended and rendered frames with `AudioTimestamp` presentation data when supported.
- [x] Capture route, backend, obtained stream properties, underruns, drops, deadline misses, render CPU, memory, thermal state, and battery.
- [ ] Measure onset shift and transient quality for 44.1↔48 kHz conversion; adopt higher-quality offline resampling or per-rate assets if budgets fail.
- [x] Keep physical recording and onset analysis scripts versioned and reproducible.
- [x] Record commit, release variant, device, OS build, route, settings, duration, method, and raw-artifact location.
- [x] Ensure measurement collection itself does not induce underruns.

### 8.2 Run pure and render acceptance suites

- [x] Re-run 12-hour scheduler simulations at every supported sample rate.
- [x] Run randomized boundary, stall, and command-atomicity suites.
- [x] Run one-hour render tests for standard maximum density and representative polyrhythms.
- [x] Run the defined UI-interaction stress workload.
- [x] Confirm zero duplicate frames, catch-up events, mixed configurations, application deadline misses, drops, and incorrect underrun recovery; classify platform underruns under TB-008.

### 8.3 Run Pixel 8a local-route qualification

- [x] Use a release build with production sounds.
- [x] Capture at least 30 cold and 30 warm starts on the built-in speaker route.
- [x] Record one-hour acoustic evidence across low, typical, and maximum event density.
- [x] Measure fitted drift, p50/p95/p99/max inter-onset error, and missed/doubled events.
- [x] Run one-hour CPU, memory, thermal, and unplugged battery measurements under documented settings.
- [x] Run the one-hour TB-017 battery sniff check under documented reference settings.
- [x] Establish Phase 8 as the TB-018 baseline; require one future matched sniff check and confirm only anomalies or near-budget results.
- [x] Record wired and USB physical latency as unmeasured because suitable phone-connected output hardware was unavailable; retain application and automated route-loss coverage.

### 8.4 Verify core lifecycle and secondary outputs physically

- [x] Verify long-duration focus acquisition, other-media pause or duck behavior, focus-loss stop, and focus release with at least one common media player.
- [x] Record USB and wired hardware as unavailable; retain automated route-loss coverage without a physical latency claim.
- [x] Verify Bluetooth stop/restart behavior and latency warning observationally.
- [x] Measure steady standard-playback visual alignment with clap-calibrated high-speed video.
- [x] Keep physical retune-boundary alignment outside the release claim; TB-009 and TB-010 retain the application/render boundary gates.
- [x] Retain the 196-event desk-rattle result as observational TB-012 evidence and the clap-calibrated 240 fps flashlight result as observational TB-013 evidence, with their limitations documented.
- [x] Verify torch failsafe under stop, background, interruption, and forced failure.

### 8.5 Publish only supported claims

- [ ] Compare every result with TB-001 through TB-018.
- [x] Record passes, failures, test limitations, and route-specific exclusions.
- [x] Do not generalize Pixel 8a speaker results to all Android devices, Bluetooth, USB, or analog output.
- [ ] Update README and release notes only with measured release-build claims.
- [ ] Turn repeatable non-acoustic regressions into automated gates.

**Exit gate**

- [ ] Every accepted release-blocking budget passes, or the contract is explicitly amended before release.
- [ ] Missing external instrumentation is treated as an evidence gap, never an assumed pass.
- [ ] The Pixel 8a completes one-hour normal and stress runs with no application-generated misses, doubles, catch-up clicks, or underruns.
- [ ] Release documentation states the tested device, route, method, and limits of every timing claim.

## Phase 9 — Complete extended iOS parity

**Purpose:** implement the valuable but non-core background, locked-device, system-control, and backing-track experiences after the metronome itself meets its release gates.

These items are committed parity work, but they do not block the core metronome release. They must use the same coordinator and quality standards; “nice to have” does not mean a second-rate implementation.

### 9.1 Implement background and locked playback

- [ ] Add the media-playback foreground-service permission and service declaration.
- [ ] Move active playback lifetime into the foreground service without creating a second coordinator or playback state.
- [ ] Continue an already active session when the Activity backgrounds or the device locks.
- [ ] Remove Activity and process-lifecycle stops only after the service owns the session safely.
- [ ] Keep haptic and flashlight effects foreground-only while audio continues.
- [ ] Keep the screen-awake preference limited to visible active playback.
- [ ] Preserve practice checkpoints while the UI is absent.

### 9.2 Add reliable system controls

- [ ] Add a persistent playback notification and media session.
- [ ] Expose stop or pause-shaped controls that end the phase and never silently resume it.
- [ ] Disable meaningless seek, skip, and playback-rate commands.
- [ ] Keep notification and lock-screen state synchronized with the coordinator.
- [ ] Handle notification permission states and current Android foreground-service restrictions.
- [ ] Test repeated start-lock-stop cycles for stale controls and leaked services.

### 9.3 Evaluate optional backing-track coexistence

- [ ] Keep long-duration `AUDIOFOCUS_GAIN` as the default and primary behavior.
- [ ] Validate user demand before adding a separate coexistence option.
- [ ] If implemented, define the option in an ADR amendment before coding it.
- [ ] Make its reduced interruption guarantees explicit in UI and documentation.
- [ ] Verify it with representative media players without weakening default focus ownership.

### 9.4 Verify extended parity physically

- [ ] Run background, lock, unlock, notification stop, explicit restart, and route-loss scenarios on the Pixel 8a.
- [ ] Verify one-hour locked playback without missed events, underruns, service termination, or accounting loss.
- [ ] Measure the foreground-service battery delta against audio-only foreground playback.
- [ ] Re-run torch and haptic failsafe tests across foreground/background transitions.

**Exit gate**

- [ ] PL-007, PL-011, PL-012, and PL-025 pass on the Pixel 8a.
- [ ] Background and locked playback use the authoritative coordinator and meet the same audio budgets as foreground playback.
- [ ] Any coexistence option is explicitly approved, tested, and secondary to default focus ownership.

## Phase 10 — Protect the repaired architecture

**Purpose:** make contract regressions harder to introduce than correct changes.

### 10.1 Enforce dependency boundaries

Required dependency direction:

```text
feature UI → playback API → music domain
audio engine → playback API + music domain
data → data/domain models, never audio engine
```

- [ ] Retain the Phase 3 architecture test that prohibits Android dependencies in the music domain as a required CI gate.
- [ ] Prohibit UI, Room, resource decode, and network dependencies from the render package.
- [ ] Prohibit feature ViewModels from depending on concrete engine implementations.
- [ ] Prohibit practice writes from direct UI start handlers.
- [ ] Extract Gradle modules only where a stable boundary improves build or enforcement; do not modularize by folder count.

### 10.2 Strengthen CI

- [ ] Run contract-tagged musical and state-machine tests on every pull request.
- [ ] Run lint, formatting, static analysis, unit tests, migration tests, and emulator lifecycle tests.
- [ ] Add release smoke builds on minimum and target API.
- [ ] Add macrobenchmarks and baseline profiles for startup and critical navigation.
- [ ] Track invariant coverage for scheduler, coordinator, and data migrations rather than a vanity total.
- [ ] Store benchmark trends and alert on material startup, render CPU, memory, and energy regressions.
- [ ] Keep proprietary production assets out of public CI and preserve the authorized local release gate.

### 10.3 Automate documentation and release truth

- [ ] Validate internal Markdown links and contract references.
- [ ] Require benchmark metadata fields for timing-result documents.
- [ ] Keep the current implementation audit updated when a phase changes conformance.
- [ ] Generate or verify a release checklist covering signing, assets, migration, privacy, device evidence, and known limitations.
- [ ] Prevent unmeasured timing superlatives in release documentation.

### 10.4 Establish idle and energy guards

- [ ] Verify no scheduler, renderer, Choreographer, service, torch, or haptic work continues while idle.
- [ ] Measure audio-only, screen-awake, background-service, haptic, and flash variants.
- [ ] Fail a maintained device benchmark on a fixed-budget violation or confirmed material regression from TB-014 through TB-018.

**Exit gate**

- [ ] Automated dependency rules protect the real-time and authoritative-state boundaries.
- [ ] CI blocks contract, migration, accessibility, lifecycle, and material performance regressions.
- [ ] Release preparation is reproducible without overstating physical-device evidence.

## Phase 11 — Add product differentiation

**Purpose:** add musician value only after the free core is trustworthy, measurable, and accessible.

Evaluate each feature against the iOS product first, then record Android-specific differences before implementation.

Candidate order:

1. performance-safe full-screen mode with large controls;
2. explicit count-in, bar counter, timer, and auto-stop;
3. programmable tempo training beyond the approved instant ramp;
4. per-beat accent/mute editor and saved presets;
5. import/export and private backup of songs and playlists;
6. hardware and media-button control;
7. optional MIDI/controller input;
8. route calibration and clearer latency confidence.

Product constraints:

- [ ] Preserve no-ads, no-tracking, offline-first behavior unless the product promise is explicitly changed.
- [ ] Write or amend a product contract before adding new musical or lifecycle semantics.
- [ ] Route every playback feature through `PlaybackCoordinator`.
- [ ] Keep all timing, energy, privacy, migration, and accessibility gates green.
- [ ] Validate usefulness with musicians before expanding a prototype.
- [ ] Compare measured usability and timing with leading Android metronomes before making best-in-class claims.

**Exit gate**

- [ ] Musicians validate the feature and its control model.
- [ ] Existing contract and release-qualification gates remain green.

## Cross-phase test matrix

Every release candidate should cover:

| Area | Required coverage |
| --- | --- |
| Musical math | All approved BPM boundaries, grooves, accents, odd meters, ramps, and polyrhythms |
| Timing | Twelve-hour simulation; one-hour render and acoustic drift, percentiles, stalls, CPU, thermal, and battery |
| Transport | Start/stop races, configuration restarts, mode changes, stale sessions, failures |
| Android audio | Long-duration focus ownership, speaker, claimed wired/USB routes, observational Bluetooth, route loss |
| Lifecycle | Core: rotation, process recreation, and no automatic resume; extended-parity releases add background, lock, and notification controls |
| Practice | Confirmed start, cumulative threshold, repeated periods, midnight, timezone, DST, process death |
| Data | Concurrent writes, version 4 migration, backup/restore, preference corruption |
| UI | TalkBack, 2× font, reduced motion, RTL, small and expanded windows |
| Outputs | Audio, mute, visual, vibration, flash alignment and failsafe |
| Release | Authorized clean production build, signing, private assets, min/target API, R8 bundle |
| Privacy | No generated cache backup; diagnostics exclude user content |

## Definition of done

A remediation item is complete only when:

- [ ] its behavior cites and satisfies an approved contract clause or a newly approved decision;
- [ ] its success, failure, cancellation, and stale-session paths are tested;
- [ ] timing-sensitive work includes the appropriate frame, device, or acoustic evidence;
- [ ] persistence work includes migration and process-death behavior where applicable;
- [ ] accessibility and localization impacts are reviewed;
- [ ] documentation and the current implementation audit are updated;
- [ ] no new warning, lint failure, hard-wrapped Markdown, or architecture violation is introduced;
- [ ] the relevant phase exit gate passes.

## Traceability to architectural review

| Review finding | Revised phase |
| --- | ---: |
| P0.1 proprietary asset provisioning | 0 |
| P0.2 non-sample-scheduled playback | 2–3 |
| P0.3 absent timing evidence | 1, 8 |
| P0.4 catch-up clicks | 2–3 |
| P0.5 split playback truth | 4 |
| P0.6 incomplete focus ownership | 4 |
| P0.7 undefined product contract | 1 |
| P1.1 shared mutable transport | 4 |
| P1.2 HandlerThread command races | 2, 4 |
| P1.3 I/O on timing paths | 3 |
| P1.4 incomplete latency compensation | 3–4, 8 |
| P1.5 unsynchronized haptic/torch | 4, 8 |
| P1.6 undefined live-change phase | 1–2 |
| P1.7 wall-clock tap tempo | 7 |
| P1.8 practice history races/counting | 5 |
| P1.9 nontransactional playlists | 6 |
| P1.10 destructive migration | 5 |
| P1.11 unsafe preference decoding | 6 |
| P1.12 backup classification | 6 |
| P1.13 foreground-only limitation | 9 |
| P1.14 accessibility gaps | 7 |
| P1.15 localization gaps | 7 |
| P1.16 stale architecture docs | 0 and ongoing |
| P2.1 weak module boundaries | 10 |
| P2.2 invisible errors | 4, 6–7 |
| P2.3 unmeasured energy use | 8–10 |
| P2.4 narrow quality automation | 0, 10 |
| P2.5 product essentials | 11 |

## Immediate next actions

Continue Phase 2 with these review-sized changes:

1. Review and merge PR 2.3a.
2. PR 2.3b: add polyrhythm events and deterministic tempo-ramp state after 2.3a is reviewed and merged.
3. Begin 2.4 only after both pure-timeline changes are reviewed and merged.
