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

CI must start from a clean checkout. Success means the public source, declared toolchain, generated resources, tests, lint, and debug build agree. It does not mean a publishable production bundle exists.

## Production bundle checklist

Build production artifacts locally or in a private release environment with authorized acoustic assets and signing material.

1. Confirm every required WAV passes validation.
2. Run unit tests, lint, and emulator instrumentation.
3. Run the physical timing suite on the Pixel 8a reference environment.
4. Generate the release Android App Bundle.
5. Verify bundle contents, versions, package, signing, shrinker output, and acoustic resources.
6. Distribute through an internal Play track and perform a smoke test.
7. Record the commit, toolchains, device result, and artifact checksum.

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
