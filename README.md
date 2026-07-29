# BeatClikr Android

BeatClikr is an offline Android metronome and practice tool built with Jetpack Compose, Hilt, Room, and a low-latency streaming `AudioTrack` engine.

Current features include:

- instant metronome with subdivisions, odd-meter accents, tap tempo, and tempo ramping;
- polyrhythms;
- acoustic and synthetic sound banks;
- song library and ordered playlists;
- practice history and reminders;
- optional visual, haptic, flashlight, mute, dark-theme, and keep-awake behavior;
- adaptive phone and tablet navigation.

The current architecture and its known deficiencies are documented in [ADVERSARIAL_PROJECT_REVIEW.md](ADVERSARIAL_PROJECT_REVIEW.md). The dependency-ordered improvement backlog is in [BEATCLIKR_ACTION_PLAN.md](BEATCLIKR_ACTION_PLAN.md). Timing claims should be based on the device measurements required by those documents, not inferred from timer implementation alone.

## Requirements

- Android Studio or Android SDK command-line tools
- JDK 17
- Android 12/API 31 or newer for installation
- Android SDK Platform 37.0 (Android 17/API 37)
- Android SDK Build Tools 36.0.0
- The proprietary BeatClikr WAV resources described below

The app supports Android 12/API 31 and newer. It compiles against API 37 while retaining target API 36 until Android 17 behavior-change testing is complete. The Gradle wrapper installs Gradle 9.4.1. Android Studio's bundled JDK 21 is also compatible for local builds, but CI runs the supported JDK 17 baseline.

## Proprietary audio resources

Production audio is proprietary and is intentionally excluded from Git by `.gitignore`. An authorized local or release build must place all 30 WAV files in:

```text
app/src/main/res/raw/
```

The required filenames are the `R.raw` identifiers declared in `SoundFile.kt`, with `.wav` appended. This consists of 15 acoustic names such as `clickhi_e5.wav` and the corresponding 15 synthetic names such as `synth_clickhi_e5.wav`.

Do not commit production WAV files. The tracked `.gitkeep` preserves the directory in a clean checkout.

### Authorized production audio setup

The tracked `audio/audio-requirements.json` defines the 30 required filenames and supported PCM formats. The production checksums, bank, PCM encoding, sample format, peak sample magnitude, exact leading-silence frames, provenance, license, and asset version live in the ignored private manifest:

```text
audio/audio-manifest.private.json
```

On the trusted source machine, create or refresh that manifest after intentionally updating the production sound set:

```bash
python3 tools/validate_audio.py \
  --create-private-manifest \
  --asset-version production-v1
```

Store the resulting private manifest with the protected source-audio archive. On another authorized release machine, provision both the WAV files and private manifest locally, then verify them:

```bash
python3 tools/validate_audio.py
```

Every release build runs this validation automatically before Android resource processing. A release fails if a required sound is missing, malformed, renamed, or differs from the private checksum manifest. `silence_d7.wav` is permitted as a legacy unused resource but is not part of the required production bank.

## Local verification

With JDK 17, the Android SDK, and the proprietary WAV files installed:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`. It uses the application ID `com.bfunkstudios.beatclikr.debug`, allowing it to coexist with a signed production installation.

Instrumented tests require a connected device or running emulator:

```bash
./gradlew --no-daemon connectedDebugAndroidTest
```

The real-engine emulator correctness harness can be run separately:

```bash
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AudioEngineInstrumentedTest
```

It verifies both sound banks decode, dense metronome callbacks remain monotonic, PCM reaches `AudioTrack`, underrun metrics are readable, and polyrhythm events are complete. Its callback-jitter threshold is intentionally broad because emulator scheduling and virtual audio cannot establish audible timing quality. Use the Pixel 8a and physical loopback procedure for publishable latency/jitter results.

Release verification should use the authorized production audio and signing process:

```bash
./gradlew --no-daemon bundleRelease
```

## Continuous integration

GitHub Actions runs [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) for every push, pull request, and manual dispatch. The workflow:

1. checks out the repository;
2. installs Eclipse Temurin JDK 17;
3. installs Android SDK Platform 37 and Build Tools 36.0.0;
4. configures Gradle caching;
5. generates non-production placeholder WAVs;
6. runs debug unit tests, Android lint, and a debug build;
7. runs the bounded instrumentation and contract suites on Android 12/API 31 and Android 17/API 37 emulators.

Public CI cannot access the proprietary BeatClikr sounds. `tools/generate_ci_audio.py` creates short placeholder PCM WAVs only so Android resource generation and code verification can run. These files are ignored by Git and must never be used for a production artifact.

The public workflow does not:

- validate production sound quality or checksums;
- run the long-duration stress, startup benchmark, or acoustic timing suites;
- build or sign a production release;
- prove audio latency, jitter, or drift.

An authorized local release environment must provision and validate the private audio resources before running `bundleRelease`. Access-controlled production-audio provisioning in GitHub CI is not required for now. See `BEATCLIKR_ACTION_PLAN.md` for the required private asset manifest and physical-device timing gates.

## Architecture

The project currently uses:

- Compose for UI and Navigation Compose for routing;
- Hilt for application-level dependency injection;
- Room for songs, playlists, and practice history;
- SharedPreferences behind `IAppPreferences`;
- `MetronomeAudioEngine` for timing and `AudioTrackEngine` for PCM rendering;
- ViewModels for feature state and coordination.

This describes the present implementation, not the target architecture. The remediation plan calls for a pure sample-frame scheduler, a single playback coordinator, transactional data mutations, and measured hardware acceptance gates.

## Documentation

| File | Contents |
| --- | --- |
| [Docs/Architecture.md](Docs/Architecture.md) | Current layers, ownership, persistence, flows, and limits |
| [Docs/Models.md](Docs/Models.md) | Room entities, relations, musical models, and schema policy |
| [Docs/ViewModels.md](Docs/ViewModels.md) | Feature state, dependency injection, and ownership |
| [Docs/Views.md](Docs/Views.md) | Compose screens, navigation, adaptive layout, and components |
| [Docs/Services.md](Docs/Services.md) | Audio, output, reminder, repository, and lifecycle services |
| [Docs/Constants.md](Docs/Constants.md) | Current constants and mathematical helpers |
| [Docs/SongLibrary.md](Docs/SongLibrary.md) | Songs, playlists, practice history, reminders, and migrations |
| [Docs/Capabilities.md](Docs/Capabilities.md) | Shipping features, permissions, storage, and background policy |
| [Docs/PlaybackPerformance.md](Docs/PlaybackPerformance.md) | Timing contract, output pipeline, metrics, and hardware protocol |
| [Docs/Timing-Budgets.md](Docs/Timing-Budgets.md) | Quantitative scheduler, acoustic, alignment, and resource acceptance gates |
| [Docs/Decisions/0001-Musical-Time.md](Docs/Decisions/0001-Musical-Time.md) | BPM, meter, accent, boundary, phase, and polyrhythm contract |
| [Docs/Decisions/0002-Playback-Lifecycle-and-Outputs.md](Docs/Decisions/0002-Playback-Lifecycle-and-Outputs.md) | Playback ownership, focus, lifecycle, routes, and secondary outputs |
| [Docs/Decisions/0003-Practice-History.md](Docs/Decisions/0003-Practice-History.md) | Practice qualification, duration, identity, and local-day behavior |
| [Docs/Decisions/Review-Guide.md](Docs/Decisions/Review-Guide.md) | Phase 1 internal findings and product-owner approval checklist |
| [Docs/Decisions/Current-Implementation-Audit.md](Docs/Decisions/Current-Implementation-Audit.md) | Clause-by-clause Android conformance and divergence audit |
| [Docs/Validation.md](Docs/Validation.md) | Local, CI, instrumentation, and production release checks |
| [benchmarks/2026-07-28-android-17-emulator.md](benchmarks/2026-07-28-android-17-emulator.md) | Current emulator correctness baseline |
| [benchmarks/2026-07-28-pixel-8a-android-17.md](benchmarks/2026-07-28-pixel-8a-android-17.md) | Initial Pixel 8a real-device engine baseline |
| [benchmarks/2026-07-28-pixel-8a-acoustic-240bpm.md](benchmarks/2026-07-28-pixel-8a-acoustic-240bpm.md) | Initial Pixel 8a built-in-speaker acoustic baseline |
| [benchmarks/2026-07-28-pixel-8a-cpu-profile.md](benchmarks/2026-07-28-pixel-8a-cpu-profile.md) | Pixel 8a CPU, memory, thermal, and profiled-load result |
| [benchmarks/2026-07-28-pixel-8a-startup-latency.md](benchmarks/2026-07-28-pixel-8a-startup-latency.md) | Pixel 8a cold and warm predicted-presentation startup baseline |
| [benchmarks/2026-07-28-pixel-8a-low-overhead-profile.md](benchmarks/2026-07-28-pixel-8a-low-overhead-profile.md) | Pixel 8a aggregate CPU and zero-underrun sustained profile |
| [benchmarks/2026-07-28-pixel-8a-battery.md](benchmarks/2026-07-28-pixel-8a-battery.md) | Pixel 8a unplugged battery, thermal, memory, and one-hour audio result |

## Database version policy

Room database version 4 is the first version released through Google Play and is the migration baseline. Pre-release database versions 1–3 are unsupported and are destructively recreated if encountered. All migrations beginning with version 4 must preserve user data and include exported schemas and instrumentation tests.

## Repository layout

```text
app/src/main/java/       Application code
app/src/main/res/        Android resources
app/src/test/            JVM unit tests
app/src/androidTest/     Instrumented and Compose UI tests
app/schemas/             Exported Room schemas
audio/                   Tracked audio contract and private source metadata
Docs/                    Architecture, timing, testing, and release guidance
benchmarks/              Environment-specific timing records
tools/                   Development and CI support scripts
.github/workflows/       GitHub Actions workflows
```

## License

Source licensing is described in [LICENSE](LICENSE). Proprietary BeatClikr audio resources are not included in that source distribution.
