# BeatClikr Android — Adversarial Project Review

**Review date:** 2026-07-27  
**Scope:** repository, build/release path, timing and audio, lifecycle, state ownership, persistence, UX/accessibility, testing, privacy, and product readiness  
**Standard applied:** “the best free metronome for Android,” not merely “a functional Compose app”

> **Progress note (2026-07-28):** This review preserves the original deficiency
> snapshot. Phase 0.3 documentation is complete, public CI is operational, all
> required audio mappings have automated validation, and real-engine emulator
> instrumentation now provides an initial correctness baseline. These advances
> do not close the sample-scheduling or physical-device evidence findings.

## Executive verdict

BeatClikr has a promising feature surface and sensible ingredients—Compose, Hilt, Room, monotonic clocks, a streaming `AudioTrack`, cached PCM, odd meters, polyrhythms, playlists, practice history, haptics, flashlight, ramping, and reminders. It is not yet architecturally credible as a best-in-class metronome.

The central problem is that the product's most important guarantee, trustworthy time, is asserted rather than measured. The timing thread decides *when to enqueue* a click, while a separate render thread decides *when to place that click at the start of a future PCM chunk*. The code does not schedule a click at a sample-frame position. Thread wake-up, queue handoff, render-chunk boundaries, AudioTrack buffering, and the device audio path all sit between the intended timestamp and the audible transient. The README's “sample-accurate,” “<5ms jitter,” and “no drift” claims are unsupported by tests or device measurements.

There is also an immediate P0 in the distribution workflow: all 30 proprietary sounds are correctly installed locally but intentionally ignored, so an authorized clean build needs a documented private provisioning step. Beyond that, playback state can lie to the UI, audio focus is not fully owned, persistence has race windows, destructive migration history exists, and the test suite largely mocks away the subsystem the app must get right.

**Recommendation:** do not market timing accuracy or ship the current release until every P0 gate below passes. Freeze feature growth for one milestone and rebuild the playback core around one sample-frame timeline with on-device measurement.

## Threat model: how this app fails a musician

The adversary is not a malicious caller. It is a real Android phone under real load:

- a scheduler pause arrives just before a beat;
- the render thread is blocked inside `AudioTrack.write`;
- Bluetooth adds large and variable latency;
- an incoming call denies or revokes audio focus;
- the user changes BPM or subdivision mid-bar;
- the app backgrounds, rotates, or restores stale navigation state;
- malformed legacy preferences survive an upgrade;
- two practice-recording coroutines race at midnight;
- a clean CI worker checks out only tracked files;
- TalkBack, large font, a small phone, or RTL exposes assumptions;
- OEM audio implementations disagree about burst size and low-latency support.

The architecture must make these cases explicit and testable. Today it mostly hopes they do not happen.

## Priority definitions

- **P0 — release blocker:** cannot build, corrupts trust/data, or invalidates the metronome's core promise.
- **P1 — required for best-in-class:** material reliability, usability, accessibility, or maintainability deficit.
- **P2 — hardening/polish:** important after the core contract is proven.

## P0 findings

### P0.1 — Proprietary asset provisioning is not reproducible

`SoundFile.kt` statically references 15 acoustic and 15 synthetic `R.raw` resources. All 30 are now present locally under `app/src/main/res/raw`, exactly match their resource identifiers, and are valid 16-bit, 44.1 kHz PCM WAV files. They are proprietary and correctly excluded from Git; `.gitkeep` preserves the required directory. `silence_d7.wav` is an additional valid file but is not referenced by current code.

The remaining blocker is process, not placement: a clean checkout lacks the resources and therefore cannot compile until an authorized developer or release worker provisions them. The README incorrectly says 15 samples are tracked in Git, and no verified private provisioning contract is present.

**Required action**

1. Keep WAVs ignored and provision them from an access-controlled artifact store or documented local source.
2. Add a script that validates exact filenames, expected count, SHA-256, PCM format, sample rate, and license/version manifest without exposing the assets.
3. Make authorized CI provision and validate assets before `processResources`; public/untrusted CI should fail with one clear setup message or use explicitly non-production test tones.
4. Add `:app:assembleDebug`, `:app:bundleRelease`, unit tests, lint, and an authorized clean-environment build to CI.
5. Correct the README: 30 proprietary production samples are required locally and are not tracked.

### P0.2 — The playback engine is not sample-scheduled

`MetronomeAudioEngine` polls a `HandlerThread` every 1 ms and calls `playBeat()` when it is within a 2 ms lookahead. `AudioTrackEngine.playBeat()` only appends a waveform to `pendingClicks`. A different thread drains that queue before its next blocking chunk write and always mixes a newly arrived click at `renderBuffer[0]`.

Consequences:

- A click can only start on a render-chunk boundary, not at its intended frame.
- The enqueue-to-render handoff adds nondeterministic scheduling delay.
- A click arriving while `AudioTrack.write(..., WRITE_BLOCKING)` is blocked waits for the next render pass.
- The 2 ms early fire is a heuristic; it is unrelated to the currently queued frame count.
- `Handler.postDelayed(1)` is not a real-time scheduling guarantee.
- `PERFORMANCE_MODE_LOW_LATENCY` is a request, not evidence that the path is low latency.

The engine may sound acceptable on some phones, but the architecture cannot substantiate “sample-accurate.”

**Required replacement**

Use one audio render owner and one frame timeline:

1. Maintain `renderedFrame`, `nextEventFrame`, phase, accent, and tempo in the render engine.
2. Generate each PCM block by inserting transients at exact offsets within that block; carry waveform tails across blocks.
3. Derive event frames from rational/fixed-point phase accumulation so fractional frames do not drift.
4. Send UI/haptic events *from the committed audio-frame schedule*, never from a separate polling scheduler.
5. Apply control changes through an immutable command queue at a documented boundary: next tick, next beat, or next bar.
6. Prefer Oboe/AAudio through the NDK for API levels/devices where it improves callback stability; retain a measured `AudioTrack` fallback. Choose by observed stream properties, not OS version alone.
7. Detect and report actual route/performance mode. Treat wired, speaker, USB, and Bluetooth as different products.

The Kotlin streaming implementation can remain as a fallback, but scheduling must happen in sample space.

### P0.3 — Timing claims have no timing tests or device evidence

There are 119 test methods, but no tests directly exercise `MetronomeAudioEngine`, `AudioTrackEngine`, `PcmFileCache`, or `PolyrhythmTimingEngine`. ViewModel tests use a fake/mock audio service. No benchmark, loopback recording, timestamp correlation, underrun counter, percentile jitter report, long-run drift test, thermal/load test, or device matrix exists.

The metrics snapshot counts queued clicks, rendered chunks, written frames, and maximum overlap. It does not record intended frame versus rendered frame, underruns, actual presentation timestamps, deadline misses, jitter distribution, or route changes.

**Required evidence**

- Deterministic host tests for the pure event/phase scheduler: accents, odd meters, polyrhythms, tempo changes, stalls, and at least 12 hours of simulated playback.
- Instrumentation tests using `AudioTrack.getTimestamp`, `getUnderrunCount`, actual buffer/performance mode, route, and frame-position error.
- Physical loopback tests on representative low-, mid-, and high-tier devices. Report p50/p95/p99 onset error and inter-onset jitter.
- Stress while scrolling, database-writing, toggling flashlight/haptics, changing theme, receiving notifications, and applying CPU/thermal load.
- Explicit acceptance budgets. A proposed starting gate for the built-in speaker/wired path is p99 inter-onset error ≤ 2 ms, zero missed beats in a one-hour stress run, and drift ≤ 1 sample-equivalent per hour in the event scheduler. Set separate honest expectations for Bluetooth.
- Publish a reproducible methodology; do not publish “<5 ms” until measurements pass.

### P0.4 — Late callbacks produce catch-up clicks instead of a defined recovery policy

Both timing engines process only one overdue event per polling iteration. After a scheduler stall, `nextBeatTimeNanos` remains in the past, so subsequent 1 ms iterations enqueue old events in a burst until caught up. A 100 ms stall at dense tempos can produce a rapid series of clicks that never belonged in audible time.

**Required action**

In the frame scheduler, detect missed deadlines before rendering:

- never render an event whose presentation window has passed;
- count and expose dropped events;
- advance phase in one operation to the first future event;
- define whether recovery preserves absolute phase or restarts on a musical boundary;
- test multi-event stalls at every supported BPM/subdivision/polyrhythm extreme.

### P0.5 — Playback truth is split, so the UI can say “playing” while silence is guaranteed

`MetronomeViewModel.start()` and `PolyrhythmViewModel.start()` set `isPlaying = true` immediately after issuing asynchronous commands. `MetronomeAudioEngine.doStart()` can return when audio focus is denied. Audio-focus loss stops engines internally without notifying either ViewModel. `MainActivity.onPause()` also stops engines directly without updating ViewModel state; process lifecycle observers attempt a second, separate stop.

This creates false transport state, false practice-history entries, and controls that need two taps to recover.

**Required action**

Make the audio session the single source of truth:

```text
Idle → Preparing → Starting → Playing → Interrupted/Stopping → Idle
                 ↘ Failed(reason)
```

Expose it as `StateFlow<PlaybackState>` with a session ID and failure reason. `start` must return/emit success only after focus and stream startup succeed. Focus loss, route loss, underrun failure, app policy, and explicit stop must traverse the same state machine. Record practice only after confirmed audible playback and a minimum meaningful duration.

### P0.6 — Audio-focus ownership is incomplete

Focus is requested on every start but abandoned only by `release()`, which a Hilt singleton is not shown calling during normal application life. Normal stop, focus loss, screen navigation, and app background do not abandon it. Duck events are treated as a hard stop without a state callback. The request uses full `AUDIOFOCUS_GAIN`, though the desired foreground-only session semantics are not documented.

**Required action**

Move focus into the playback session state machine. Pair every granted request with abandon, serialize requests, handle delayed focus if enabled, and test denial/loss/transient loss/duck/gain. Decide and document whether the app pauses and can resume or stops permanently. Never let focus callbacks mutate the engine without emitting authoritative state.

### P0.7 — The “best metronome” product contract is undefined

The code supports 30–240 BPM, several grooves, patterns, polyrhythm, ramping, visual/haptic/flash feedback, songs, and playlists. There is no written contract for:

- whether BPM is quarter-note BPM or pulse BPM in odd meters;
- when a live tempo/subdivision/pattern change takes effect;
- phase behavior after interruptions and stalls;
- audio behavior under Bluetooth latency;
- maximum acceptable jitter/drift;
- whether background/lock-screen playback is intentionally unsupported;
- practice-session semantics;
- accessibility and offline/privacy guarantees.

Without this, tests cannot distinguish defects from unspecified behavior.

**Required action**

Create an architecture decision record (ADR) for the timing contract and a product quality specification. Every timing-related issue should map to a measurable clause.

## P1 findings

### P1.1 — Two ViewModels share one mutable singleton transport

`MetronomeViewModel` assigns `audio.delegate`; `PolyrhythmViewModel` assigns `audio.polyrhythmDelegate`; both mutate shared sounds, mute, and sound bank. Navigation manually stops selected modes, while compact layout hosts both modes in one container. This is temporal coupling: correctness depends on composition and navigation order.

Use a single application-scoped `PlaybackCoordinator` with typed commands and one state stream. UI-specific ViewModels should submit intents and observe session state; they should not own engine delegates or global audio configuration.

### P1.2 — Stop/start commands race across two HandlerThreads

The timing handler posts `AudioTrackEngine.start()`/`stop()` onto the render handler and immediately proceeds. Rapid mode switches can queue stop/start/stop operations whose eventual render order is decoupled from the timing engine's local flags. There is no generation/session token to discard stale commands or callbacks.

Tag all commands and callbacks with a monotonically increasing session ID. Serialize state transitions in one owner. Ignore events from superseded sessions.

### P1.3 — Sound changes can perform file I/O and decoding on timing-sensitive paths

`AudioTrackEngine.setSounds()` calls `ensureWaveform()` synchronously on the metronome handler. A cache miss reads a resource, parses WAV, resamples, writes to `filesDir`, and rereads the PCM file. `prepareAudioTrackSounds()` redundantly prepares through the timing handler and again through the render handler. A missing/failed resource silently becomes an empty waveform.

Decode and validate all selectable sounds off the audio/timing threads. Atomically publish immutable waveform banks. Startup must fail visibly if required audio is absent. Remove redundant preparation and add cache integrity/version tests.

### P1.4 — Latency compensation is knowingly incomplete but presented as synchronization

`estimatedOutputLatencyNanos` is `(minimum buffer frames + output burst frames) / sample rate`. The code itself notes this is only a lower bound and excludes HAL/device latency. It also ignores the instantaneous number of queued frames. Visual timestamps therefore can be early or late by device- and route-dependent amounts.

Use actual `AudioTimestamp` frame position/time correlation where supported, continuously filter it, and degrade honestly where unsupported. Provide user calibration for audio/visual/haptic alignment. Never use the buffer-size estimate as a precision claim.

### P1.5 — Haptics and flashlight are UI-thread side effects, not synchronized outputs

Beat callbacks launch main-thread coroutines. Haptics and torch commands occur when that coroutine runs, not at the audio event's presentation time. Flashlight “on” is turned off only on a subdivision callback or stop; quarter-note mode can leave it on for the entire run. Polyrhythm does not use haptic/flash feedback at all.

Create an output scheduler with per-output latency calibration and bounded pulse durations. Torch-off must have its own failsafe timer. Treat haptic and flashlight timing as approximate and test OEM behavior.

### P1.6 — Tempo and pattern changes have undefined phase semantics

`updateTempo()` replaces values without recalculating `nextBeatTimeNanos`. The next interval is anchored to the old schedule but uses the new duration after that. Subdivision/pattern changes retain a counter unless it happens to exceed the new pattern size. Musically, controls may jump into the middle of a new grouping.

Make change boundaries explicit in the command (`Immediate`, `NextTick`, `NextBeat`, `NextBar`). The scheduler should calculate the exact effective frame and reset/translate phase deterministically.

### P1.7 — Tap tempo uses a wall clock and an outlier-sensitive mean

`recordTap()` uses `System.currentTimeMillis()`, which can jump, and averages every retained interval. One accidental tap contaminates the result for up to eight taps.

Use `elapsedRealtimeNanos()`, reject implausible intervals, use a robust estimator (median or trimmed/weighted mean), provide confidence/reset feedback, and test double taps and tempo transitions.

### P1.8 — Practice history is vulnerable to races and over-counting

History records immediately on a start request, even if focus is denied or playback is stopped instantly. “Practice” can therefore mean one tap on Play. `getOrCreateTodaysSession()` is a read-then-insert sequence without a unique day key or transaction. Concurrent starts can create multiple sessions for the same local day. Song increment is another read-modify-write race.

Store a normalized `localDate` plus timezone metadata, enforce uniqueness, and use DAO transactions/atomic SQL updates. Track start/end/duration and record only after a configurable threshold. Define travel and daylight-saving behavior.

### P1.9 — Playlist reorder/delete is not transactional

Delete and resequence are separate repository calls. Reorder uses a batch `@Update`, but no transaction protects related operations, no unique `(playlist_id, sequence)` invariant exists, and concurrent `addSong()` calls derive the same `count`.

Move playlist mutations into transactional DAO methods. Enforce ordering invariants, resolve collisions deterministically, and test interrupted/concurrent mutations.

### P1.10 — Resolved: version 4 is now the production migration baseline

Versions 1–3 were never released through Google Play, their schemas were never exported, and their provisional migrations included destructive behavior. Those migrations and invalid tests have now been removed. Room explicitly permits destructive recreation only when starting from versions 1–3; version 4 is documented as the first production baseline.

Every migration beginning with version 4 must preserve user-authored data and include its exported schema, production-like fixture, and instrumentation test. Do not broaden the destructive fallback beyond versions 1–3.

### P1.11 — Preference deserialization can crash app startup

Most enum preferences use `Enum.valueOf(storedValue)!!`. Renamed enum constants, corrupted XML, restored backups from another version, or historical values can crash ViewModel construction and potentially the app. `SoundBank` alone uses a safe fallback.

Centralize versioned preference codecs with `runCatching`, validation/clamping, migration, and telemetry-safe diagnostics. Prefer DataStore for typed, transactional updates, but a safe codec matters more than the storage brand.

### P1.12 — Backup policy includes undocumented user data and generated audio cache

`allowBackup=true`; PCM files are deliberately written under `filesDir`, and the backup rules do not visibly establish a product-level data classification in this review. Practice history, songs, playlists, preferences, and generated PCM need intentional include/exclude decisions. Generated PCM should never consume backup quota.

Document privacy behavior, exclude regenerable audio cache, verify restore across versions, and add a short privacy policy even if the app collects no server-side data.

### P1.13 — Foreground-only playback undermines common metronome use cases

The app stops when the process/activity pauses. That prevents reliable practice with the screen locked or while viewing sheet music/recording in another app. A best-in-class metronome should either support a foreground media-style service with notification controls and audio focus or state foreground-only behavior as a deliberate limitation.

If background playback is added, it must be a real foreground service with media controls, correct Android 13+ notification handling, lifecycle tests, and no phantom service. If it remains unsupported, remove any implication of parity with professional metronomes.

### P1.14 — Accessibility is not an acceptance gate

Many controls use null content descriptions for plus/minus, transport, sound picker, and playback icons. Some may inherit semantics from clickable parents, but this is not demonstrated. Five compact bottom-navigation items crowd phones; polyrhythm is hidden from the compact tabs and nested elsewhere. Fixed layouts, dynamic text, touch-target size, TalkBack order, switch labels, high contrast, reduced motion, and keyboard/switch access lack systematic tests.

Add accessibility semantics intentionally at the control level and automated checks. Manually certify TalkBack, font scale 2.0, display scaling, RTL, color-blind contrast, landscape, tablets/foldables, and one-handed use. Announce beat indicators sparingly; never flood TalkBack every tick.

### P1.15 — Localization is partial and domain strings are embedded in code

English display names live in enums (`SoundFile`, likely other domain enums), while only base and Spanish string resources exist. This prevents complete localization and can leak English into Spanish UI.

Domain models should expose stable IDs; UI maps IDs to localized resources. Add pseudolocale tests and CI checks for missing translations.

### P1.16 — Architecture documentation is stale enough to be dangerous

The README says SoundPool is the active engine, describes files/classes that no longer match the repository, lists already-implemented features as roadmap work, says sounds are tracked when they are not, and claims release qualities without evidence.

Replace it with a short current architecture, setup/build truth, timing contract, known limitations, and links to ADRs and benchmark results. Documentation claims should be CI-verifiable where possible.

## P2 findings

### P2.1 — Module boundaries do not protect the core

Everything lives in one `:app` module. Android UI, Room, preferences, scheduling, musical math, and render policy are easy to couple and hard to benchmark independently.

Suggested modules:

```text
:app
  ├─ :feature:metronome
  ├─ :feature:polyrhythm
  ├─ :feature:library
  ├─ :feature:practice
  ├─ :core:music-domain
  ├─ :core:playback-api
  ├─ :core:audio-engine
  ├─ :core:data
  └─ :core:design-system
```

Do not modularize for aesthetics. First extract pure music scheduling and the playback contract so they can be fuzzed, benchmarked, and reused without Android UI.

### P2.2 — Error handling is mostly invisible

Audio decode failures become empty waveforms, flashlight errors are swallowed, coroutine repository failures do not become user-visible state, and reminders/audio focus have no unified diagnostic surface.

Define typed failures, safe user messages, retry policy, and privacy-preserving local diagnostics. A “Copy diagnostics” action should include app version, device/audio route, stream properties, underruns, and scheduler statistics—never song/user content unless explicitly included.

### P2.3 — Energy use is unmeasured

A 1 ms polling loop plus continuous silence rendering, per-frame Compose animation, torch, haptics, and keep-awake can be expensive. The app has no power benchmark or adaptive idle policy.

The callback/frame scheduler removes polling. Stop rendering/animation when idle, measure energy during a one-hour practice, and document battery impact of torch/keep-awake.

### P2.4 — Quality automation is too narrow

There is no visible CI configuration, formatting/static-analysis policy, dependency update policy, baseline profile, macrobenchmark, screenshot regression suite, release smoke test, or reproducible release documentation.

Add these only after the P0 playback contract is fixed; passing many shallow checks is not a substitute for timing evidence.

### P2.5 — Product essentials need explicit decisions

To compete for “best free,” evaluate—not blindly add:

- setlist-safe full-screen mode with huge controls and orientation lock option;
- count-in, timer, bar counter, auto-stop, and programmable tempo trainer;
- per-beat accent/mute editing and saved presets;
- hardware/media-button and optional MIDI/Bluetooth controller input;
- user calibration and route warnings;
- import/export/backup for songs and playlists;
- zero ads, zero tracking, offline-first behavior, and a durable privacy promise.

Each feature must preserve the timing budget and one-source-of-truth playback state.

## Target architecture

```text
Compose screens
      │ intents / StateFlow
      ▼
Feature ViewModels
      │ typed PlaybackCommand
      ▼
PlaybackCoordinator ───────► PlaybackState + diagnostics
      │ session-id command queue
      ▼
Sample-frame Scheduler (pure Kotlin/C++)
      │ exact event offsets in render blocks
      ▼
Audio backend
  Oboe/AAudio preferred where measured
  AudioTrack fallback
      │ committed frame timestamps
      ├────────► visual clock mapper
      ├────────► haptic scheduler
      └────────► flashlight scheduler

Room/DataStore are outside the real-time path.
No allocation, file I/O, database work, or UI callbacks occur on the audio callback.
```

### Core contracts

`PlaybackCommand` should include session ID, requested musical boundary, and immutable configuration. `PlaybackState` should include authoritative transport state, active configuration, route, latency confidence, and interruption/failure. `TimingDiagnostics` should include intended/rendered/presented frames, deadline misses, dropped events, underruns, route changes, and percentile summaries.

The scheduler must be platform-independent and deterministic. Android-specific audio code adapts scheduler blocks to the device stream; it does not decide musical phase.

## Recommended execution order

### Milestone 0 — Make the repository real (1–2 days)

- Document private asset provisioning and add a checksum/format/name validator.
- Add CI for debug, release bundle, unit tests, lint, and migration tests.
- Rewrite false README statements.
- Capture a tagged baseline APK and baseline measurements for comparison.

### Milestone 1 — Specify and test musical time (3–5 days)

- Write timing/product ADRs.
- Extract a pure frame scheduler.
- Add property/fuzz tests, long-run drift simulation, change-boundary tests, and stall recovery tests.
- Define release budgets and device/route matrix.

### Milestone 2 — Replace the render path (1–2 weeks)

- Integrate scheduler directly into render blocks.
- Add session IDs, command queue, authoritative state machine, focus ownership, and route handling.
- Remove the 1 ms polling scheduler.
- Predecode immutable sound banks outside real-time threads.
- Instrument timestamps, deadlines, and underruns.

### Milestone 3 — Prove it on hardware (1 week, parallelizable across devices)

- Build loopback harness and benchmark protocol.
- Test speaker, wired, USB, and Bluetooth separately under load.
- Fix p99 outliers and publish honest results.
- Add user calibration and route warnings where deterministic alignment is impossible.

### Milestone 4 — Data/lifecycle/accessibility hardening (1 week)

- Transactional practice/playlist mutations and safe preference codecs.
- Decide foreground service versus explicit foreground-only limitation.
- Fix backup classification, restore, migrations, and privacy documentation.
- Run accessibility and adaptive-layout acceptance matrix.

### Milestone 5 — Product differentiation

- Add only features validated by musicians.
- Run blinded timing/usability comparisons against leading Android metronomes.
- Maintain timing, energy, accessibility, and clean-build gates on every release.

## Release gates

No production release should occur until:

- [ ] An authorized fresh environment provisions private assets and builds debug and signed-equivalent release artifacts reproducibly.
- [ ] Every bundled asset passes the private name/format/checksum/version manifest.
- [ ] The pure scheduler passes long-run drift, fuzz, stall, tempo-change, odd-meter, and polyrhythm tests.
- [ ] Physical-device results meet documented p95/p99 timing budgets on the supported low-latency routes.
- [ ] No missed/doubled/catch-up clicks occur in a one-hour stress test.
- [ ] Audio-focus denial/loss, route change, backgrounding, and rapid mode switching never desynchronize transport state.
- [ ] Practice and playlist writes are transactional and migration fixtures preserve user-authored data.
- [ ] TalkBack, 2× font, RTL, phone/tablet/foldable, and touch-target checks pass.
- [ ] Backup/restore, privacy, diagnostics, and generated-file exclusions are documented and verified.
- [ ] README claims match measured behavior.

## What is worth preserving

The review is intentionally severe, but several choices are good foundations:

- Monotonic time is the correct clock family.
- Incrementing from scheduled phase rather than callback wall time is directionally correct.
- Cached mono PCM and waveform-tail mixing are appropriate building blocks.
- The code already separates an audio service interface from UI consumers.
- Hilt, Room schema export, explicit migrations, immutable Compose state patterns, and fake services enable further hardening.
- Odd-meter patterns, polyrhythms, ramping, playlists, history, reminders, and multi-sensory feedback form a stronger free feature set than a bare clicker.

Preserve the product ambition and musical-domain work. Replace the unverifiable timing mechanism and ambiguous ownership around it.

## Verification note

This review is based on static inspection of all repository areas listed in scope, targeted line-level inspection of the timing/render/ViewModel/data paths, resource and Git tracking checks, and test inventory. A local `./gradlew test lint` attempt could not start because this workstation session has no discoverable Java runtime. The 30 required proprietary resources were subsequently verified locally for exact names and supported PCM format; they remain intentionally ignored, so clean environments require authorized provisioning. No claim in this review treats the existing test suite as executed.

### Verification update — 2026-07-28

The documented JDK 17 environment now passes unit tests, Android lint, and debug
assembly. GitHub CI also passes from a clean public checkout using explicitly
non-production generated audio. Emulator instrumentation directly exercises PCM
decoding, `MetronomeAudioEngine`, `AudioTrackEngine`, and polyrhythm scheduling;
the result is recorded under `benchmarks/`.

No physical Pixel 8a result, acoustic loopback measurement, long-run drift
result, thermal/load result, or multi-device matrix exists yet. The original P0
timing verdict therefore remains open.
