# Android 17 Emulator Audio Correctness Baseline

**Date:** 2026-07-28  
**Source commit:** `3de7a4be0a744d52696b210f7d80627ef17ec337` plus the uncommitted instrumentation harness  
**AVD:** Pixel 10 Pro (`sdk_gphone16k_arm64`)  
**API:** 37  
**Build fingerprint:** `google/sdk_gphone16k_arm64/emu64a16k:17/CP31.260623.005/15817740:user/dev-keys`

## Command

```bash
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AudioEngineInstrumentedTest
```

## Results

- 3/3 tests passed.
- All 30 required acoustic/synthetic WAV resources decoded into non-empty PCM.
- Dense metronome: 48/48 callbacks received at 240 BPM with sixteenth subdivisions.
- Callback interval p95 error: 0.964833 ms.
- `AudioTrack` underruns: 0.
- Rendered chunks observed: 291.
- Polyrhythm: beat-only, rhythm-only, and coincident events were all observed with monotonic scheduled times.

## Interpretation

This is an emulator correctness result only. Callback timing is measured in software before physical audio presentation. Virtualized `AudioTrack` output cannot establish speaker, wired, USB, or Bluetooth onset latency/jitter and must not be used in product timing claims.

The next baseline must run the same harness on the Pixel 8a, followed by a physical loopback recording to measure audible click onsets.
