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

The Phase 2.4 command suite verifies stale-session rejection, strict same-boundary ordering, atomic final configuration, standard and polyrhythm phase restarts, stable logical playback identity, deferred mute, and two-stage sound preparation and publication. These commands remain Android-free and are not connected to production playback until Phase 3.

`PlaybackInputBoundaryTest` verifies that invalid external configurations and command batches become typed failures before render handoff, valid values remain available to production consumers, and unexpected implementation exceptions are not mislabeled as recoverable input errors.

The Phase 3.1 renderer suite verifies exact in-block offsets, adjacent-block waveform tails, reset and discontinuity handling, coincident and overlapping voices, final-stage saturating conversion, mute handling, silent failure blocks, and zero measured JVM allocation while rendering from a real Phase 2 timeline. Its architecture tripwire scans `FramePcmRenderer.kt` for common locking, sleeping, logging, file/database access, and thread-handoff tokens; it does not replace review of event-source implementations.

The Phase 3.2 sound suite verifies WAV structure and format failures, empty resources, leading-silence preservation, stereo downmixing, resampling, immutable snapshots, atomic replacement, cache regeneration and versioning, bank switching, concurrent preparation, and failure without partial publication. Its architecture test prevents decoder or cache access from returning to the current `AudioTrack` render loop.

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

The emulator matrix runs the bounded instrumentation and contract suites at the supported floor, Android 12/API 31, and the current target, Android 16/API 36. Android 17/API 37 remains a manual compatibility check until its CI emulator is stable. Long-duration stress, startup benchmark, and acoustic timing tests remain explicit physical-device or performance runs.

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

Each record should state:

- date and Git commit;
- device or emulator model;
- Android version and build;
- build variant and audio route;
- exact test and duration;
- callback error, underruns, drift, and any acoustic measurement;
- limitations or environmental changes.

Keep separate emulator-correctness and physical-audio baselines. Create a new record when the toolchain, OS, device, or timing design changes.
