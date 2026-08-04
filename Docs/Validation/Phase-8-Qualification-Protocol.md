# Phase 8 Release Qualification Protocol

## Behavioral authority

Approved Android contracts and the behavior of `origin/release/4.1.0` define expected behavior. When that release differs from the sibling iOS app, the Android release behavior wins. iOS remains the reference only where the Android release and approved contracts are silent.

## Evidence layers

Pure scheduler tests prove frame arithmetic, boundary behavior, stall recovery, and command atomicity. Render tests prove intended, rendered, written, and timestamp-correlated presentation accounting. Microphone or synchronized external measurements alone support acoustic, visual, haptic, or flash timing claims.

Callback arrival distributions are diagnostic evidence and must not be labeled acoustic jitter. A passing JVM or instrumentation test cannot close a physical-measurement evidence gap.

## Device-run capture

Before every physical run, `tools/run_phase8_device_test.sh` requires exactly one active ADB transport. It records the source commit and working-tree state, device and OS build, display settings, connectivity, battery, thermal state, and audio state before and after the Gradle command. The wrapper keeps the plugged-in device awake by default and restores the prior keep-awake setting afterward. Its expensive platform snapshots occur outside the measured workload.

Screen-on, awake, and unlocked is the default normal-use condition. Use `PHASE8_SCREEN_MODE=unchanged` only for an explicitly labeled audio-only, screen-off, lifecycle, or unplugged battery variant. Screen-off evidence does not replace the normal-use or UI-stress gates.

Run the release-equivalent startup distribution with production sounds:

```bash
tools/run_phase8_device_test.sh startup-built-in -- \
  --no-daemon :app:connectedBenchmarkAndroidTest \
  -Pbeatclikr.testBuildType=benchmark \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AudioStartupLatencyInstrumentedTest
```

Run a one-hour maximum-density render test:

```bash
tools/run_phase8_device_test.sh dense-built-in-60m -- \
  --no-daemon :app:connectedBenchmarkAndroidTest \
  -Pbeatclikr.testBuildType=benchmark \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AudioEngineStressInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.stressDurationMinutes=60
```

Long runs should be launched in the background with console output redirected to a timestamped file. Retain the wrapper artifact directory and the Android test result XML with the benchmark record.

## Acoustic analysis

`tools/analyze_acoustic_onsets.py` performs streaming 16-bit PCM analysis, so a one-hour recording does not need to be loaded into memory. It computes a first-difference RMS envelope, applies a documented threshold and minimum peak distance, compares like timbres through the grouping parameter, and reports the detected count, fitted interval and drift, and absolute inter-onset error percentiles.

For the existing 240 BPM sixteenth-note recording:

```bash
tools/analyze_acoustic_onsets.py \
  benchmarks/2026-07-28-pixel-8a-acoustic-240bpm.wav \
  --interval-ms 62.5 --grouping 4 --threshold 0.003 \
  --minimum-distance-ms 45
```

Run a threshold sweep and manually review anomalies before accepting a recording. Record the WAV checksum, recording clock and interface, microphone placement, expected onset count, detector parameters, and tool commit. Different click timbres may have repeatable attack offsets, so compare like positions or use one identical sound when the contract permits it.

`tools/analyze_resampling.py` mirrors the production decoder's linear conversion for offline screening. Run every production sound in both banks at 44.1 and 48 kHz targets and retain its CSV output:

```bash
tools/analyze_resampling.py app/src/main/res/raw/*.wav \
  --target-rate 44100 --target-rate 48000 > resampling.csv
```

Review onset shift, peak delta, crest-factor ratio, derivative-RMS ratio, and round-trip normalized error per asset. This characterization does not set its own product threshold and does not prove physical output quality. Listen to flagged transients and compare recorded device output at both obtained rates. If audible or measured degradation prevents the timing budgets from passing, replace linear conversion with a documented higher-quality offline process or provide per-rate assets.

## Resource measurements

Normal-use and UI-stress gates keep diagnostics buffered in the app and prohibit continuous `dumpsys`, debugger attachment, or profiler polling. CPU, memory, thermal, and battery capture must use a separately documented low-overhead method. Any collector that causes an underrun invalidates that run rather than excusing the underrun.

Battery runs must record brightness, volume, timeout, route, radio state, battery health, duration, and charger state. Restore every changed device setting after the run. Three matched one-hour runs are required for TB-017, and TB-018 comparisons use identical settings and workloads.

## Workload matrix

| Workload | Configuration | Duration | Screen | Primary evidence |
| --- | --- | ---: | --- | --- |
| Standard low density | 30 BPM, quarter notes, acoustic bank | 20 minutes acoustic | On | TB-004–TB-006 low-density population |
| Standard typical | 120 BPM, eighth notes, representative alternating beat/rhythm sounds | 20 minutes acoustic | On | TB-004–TB-006 typical population |
| Standard maximum | 240 BPM, sixteenth notes | 60 minutes render; 20 minutes acoustic | On | TB-004–TB-006 and TB-008 maximum density |
| Polyrhythm coprime | 120 BPM, 5:7 | 60 minutes render | On | Independent voice sequences and coincidences |
| Polyrhythm dense | 240 BPM, 15:14 | 60 minutes render | On | Maximum supported polyrhythm event pressure |
| Normal interaction | 240 BPM, sixteenth notes with ordinary observation | 60 minutes | On | TB-008, TB-014–TB-016 |
| UI interaction stress | Sequence below, starting at 240 BPM sixteenths | 60 minutes | On | TB-008–TB-010 under control and rendering load |
| Unplugged battery | Matched maximum-density audio workload | 60 minutes ×3 | Documented | TB-017 |
| Matched comparator | Identical before/after workload and settings | At least three noisy-metric runs | Matched | TB-018 |

The three acoustic density recordings total at least one hour. Record each population separately and combined; a combined percentile must name its weighting. Standard and polyrhythm render results are not interchangeable.

## UI-interaction stress sequence

The workload must preserve the established 4.1.0 behavior. Top-level navigation intentionally stops playback, so it belongs in lifecycle qualification and not inside the continuous-playback stress gate.

Repeat a five-second update cycle for one hour while the app remains on the metronome screen:

1. Start at 240 BPM with sixteenth subdivisions and confirm authoritative `Playing`.
2. Change 240→239→240 BPM and verify each standard update preserves the same session and commits the complete requested configuration.
3. Change sixteenth→eighth→sixteenth and verify each update preserves the same session and commits atomically.
4. Recreate the Activity every five minutes and verify the same authoritative session remains active without a physical restart.
5. Leave controls idle for the balance of each five-second cycle while the beat animation and accessibility semantics remain active.

Automation may vary action timing with a recorded seed, but not the action set or expected behavior. Capture session ID, configuration sequence, intended/rendered/written frames, duplicate and mixed-configuration counts, catch-up/drop/deadline counters, platform underruns and recovery skips, Activity recreation count, and physical engine start/stop counts. Acceptance requires one session, one physical start, no physical stop before the final explicit stop, zero duplicate frames, mixed configurations, catch-up output, drops, or deadline misses, and exact accounting across any recovery skip. A platform underrun is classified across repeated and acoustic evidence under TB-008 rather than failing the run by count alone. Sound-picker, mute-preference, top-level navigation, and mode-switch behavior are tested separately because those controls are not present on the active standard screen or intentionally stop playback under 4.1.0.

## Physical lifecycle and output checklist

- [ ] Confirm one USB ADB transport and record device/build identity.
- [ ] Start common media, start BeatClikr, and record whether the other player pauses or ducks.
- [ ] Deliver permanent, transient, and duck-capable focus loss; require authoritative stop and no silent resume.
- [ ] Stop BeatClikr and verify focus release with the media player.
- [ ] Remove wired or USB output during playback when hardware is available; require one stop and explicit restart.
- [ ] Connect and remove Bluetooth observationally; verify the latency warning and explicit-restart policy without a fixed transport-latency claim.
- [ ] Record standard and polyrhythm retunes with high-speed video and measure audio/visual boundary skew.
- [ ] Record visual onset against predicted presentation and report display refresh rate.
- [ ] Measure haptic only with a suitable sensor; otherwise record an evidence gap.
- [ ] Measure flash only with a photodiode or suitable high-speed capture; otherwise record an evidence gap.
- [ ] Verify torch off after explicit stop, background transition, interruption, and forced torch failure.
- [ ] Restore and record brightness, volume, timeout, orientation, connectivity, route, keep-awake, and charger state.

Use [`Phase-8-Evidence-Register.md`](Phase-8-Evidence-Register.md) for TB status and `benchmarks/PHASE_8_REPORT_TEMPLATE.md` for each accepted or failed run.
