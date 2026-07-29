# Playback Performance

## Timing contract

The accepted normative contracts are [`Decisions/0001-Musical-Time.md`](Decisions/0001-Musical-Time.md), [`Decisions/0002-Playback-Lifecycle-and-Outputs.md`](Decisions/0002-Playback-Lifecycle-and-Outputs.md), and [`Decisions/0003-Practice-History.md`](Decisions/0003-Practice-History.md). Quantitative release gates are in [`Timing-Budgets.md`](Timing-Budgets.md).

The current engine schedules against a monotonic nanosecond clock. Wall-clock time is only for user-visible dates and history; it must not drive beat intervals. Tempo conversion advances from the previous scheduled deadline rather than callback time, so callback delay does not normally accumulate as drift.

```text
intervalNs = 60,000,000,000 / bpm
```

Subdivisions and polyrhythms derive deadlines from that interval. The replacement scheduler must meet contract clauses MT-030 through MT-032 by dropping expired events, avoiding catch-up bursts, preserving the session time base, and carrying fractional sample-frame remainder.

## Output pipeline

WAV files are decoded to PCM and cached in persistent internal storage. This avoids cache eviction causing an unexpected decode spike during playback. The mixer selects samples, combines coincident events, clips safely, and streams fixed-size chunks to `AudioTrack`.

`AudioTrack` underruns and playback timestamps are diagnostic signals. The engine's latency estimate is only a lower bound because device DSP, hardware, speaker, and acoustic delay are outside its measurement.

The first and later beat callbacks use the same scheduled clock as audio. ViewModels convert timestamps between engine and UI clock domains only at explicit boundaries.

## Secondary feedback

Visual, haptic, and camera-flash feedback are secondary beat-event consumers. They cannot be assumed to occur simultaneously with speaker output:

- Compose rendering waits for a display frame.
- Vibration APIs and hardware add device-dependent latency.
- Torch activation passes through camera services and hardware.
- Speaker output includes buffered and hardware latency.

Tests must measure each path independently. An on-time visual callback does not prove that the acoustic transient was on time.

## Audio assets

The acoustic assets are proprietary and intentionally untracked. Their canonical names and format constraints are in [`audio/audio-requirements.json`](../audio/audio-requirements.json). The actual WAV resources remain ignored, while `.gitkeep` preserves the resource directory.

Release builds validate the real files. CI generates deterministic, non-proprietary tones satisfying the same naming and decoding contract. Public CI therefore proves code and resource wiring, not production sound quality.

## What current tests establish

The emulator instrumentation harness exercises the real PCM decoder and audio engines. Its baseline covers every required sound, dense standard scheduling, polyrhythm scheduling, callback interval error, underruns, and output chunks.

The standard-metronome contract suite additionally covers the 30–240 BPM bounds, a 137.5 BPM decimal fixture, all four standard subdivision counts, tick-zero starts, beat/rhythm sound roles, mute event and phase continuity, stop/reset behavior, and the absence of an implicit count-in. The same focused suite passes against debug and minified release-equivalent builds on the Android 17 emulator.

The recorded result is in [`benchmarks/2026-07-28-android-17-emulator.md`](../benchmarks/2026-07-28-android-17-emulator.md). It is a regression reference, not a physical-device audio benchmark.

The first Pixel 8a engine result is in [`benchmarks/2026-07-28-pixel-8a-android-17.md`](../benchmarks/2026-07-28-pixel-8a-android-17.md). It exercises real hardware but still measures software callbacks rather than acoustic onset.

The first microphone-recorded speaker result is in [`benchmarks/2026-07-28-pixel-8a-acoustic-240bpm.md`](../benchmarks/2026-07-28-pixel-8a-acoustic-240bpm.md). It measures audible onset intervals but not absolute input-to-sound latency.

The pre-Phase-3 release comparator is in [`benchmarks/2026-07-28-pixel-8a-release-comparator.md`](../benchmarks/2026-07-28-pixel-8a-release-comparator.md). Its raw directory pins the commit, release-equivalent build, device fingerprint, route, settings, workloads, and exact commands required by TB-018.

## Low-overhead resource protocol

Resource comparisons use the non-debuggable, minified, resource-shrunk `benchmark` variant. This variant inherits `release`, has a separate application ID and local installation signature, and is profileable by shell so device-side aggregate counters can observe it without a debugger.

Run the maximum-density workload at 240 BPM with sixteenth subdivisions. Use one continuous `simpleperf stat --app` interval for aggregate CPU, take memory and thermal snapshots only after warm-up and near the end, and collect the engine's buffered underrun and timing metrics after completion. Do not continuously poll `dumpsys` or attach Android Studio's interactive profiler.

Battery is a separate physically unplugged run over wireless debugging. Record start and end charge counter, displayed percentage, battery temperature, Android thermal status, route, media volume, brightness mode and value, screen state, radio state, and the complete observation window. Restore changed settings after collection.

## Physical-device validation

The Pixel 8a running Android 17 is the initial reference device. Record the OS build, app commit, build variant, audio route, volume, battery mode, and method with every result. Test the built-in speaker first; Bluetooth is a separate latency profile.

At minimum, validate:

1. sample decoding and selection;
2. steady low, typical, and high tempos;
3. dense subdivisions and coincident polyrhythms;
4. start, stop, tempo change, backgrounding, and audio-focus interruption;
5. long-run drift, underruns, and audible artifacts;
6. visual, haptic, and flash alignment as separate measurements.

`AudioEngineStressInstrumentedTest` runs the dense 240 BPM/sixteenth path for 30 minutes by default. It requires complete callbacks, monotonic scheduled time, scheduled drift and interval error within 2 ms, written audio frames, and zero reported `AudioTrack` underruns.

Do not claim sub-millisecond acoustic timing from callback metrics. Measuring sound leaving the device requires loopback recording or an external rig.
