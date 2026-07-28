# Pixel 8a Android 17 Audio-Engine Baseline

**Date:** 2026-07-28  
**Source commit:** `49cb82962a3774669b7fd6db43971fdb2779406b` plus the uncommitted debug-isolation and documentation changes  
**Device:** Pixel 8a (`akita`)  
**Android:** 17 / API 37  
**Build:** `CP2A.260705.006`  
**Build fingerprint:** `google/akita/akita:17/CP2A.260705.006/15641320:user/release-keys`  
**Variant:** Debug (`com.bfunkstudios.beatclikr.debug`)  
**Audio route:** Built-in speaker; Bluetooth route absent  

## Command

```bash
./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AudioEngineInstrumentedTest
```

## Results

- 3/3 tests passed in 6.044 seconds.
- All 30 required acoustic/synthetic WAV resources decoded into non-empty PCM.
- Dense metronome: 48/48 callbacks at 240 BPM with sixteenth subdivisions.
- Callback interval p95 error: 0.938354 ms.
- `AudioTrack` underruns: 1.
- Rendered chunks: 1,213.
- Polyrhythm: 18/18 events, including beat, rhythm, and coincident events, with
  monotonic scheduled times.

## Unlocked-screen repeat

Five consecutive reruns were performed with the screen unlocked. All 15 test
executions passed.

| Run | Callback p95 error | Underruns | Rendered chunks |
| ---: | ---: | ---: | ---: |
| 1 | 1.123657 ms | 0 | 1,224 |
| 2 | 3.814494 ms | 0 | 1,204 |
| 3 | 2.531860 ms | 0 | 1,220 |
| 4 | 1.251017 ms | 0 | 1,216 |
| 5 | 1.129720 ms | 0 | 1,205 |

Across the repeat, the median per-run p95 callback error was 1.251017 ms, the
mean was 1.970150 ms, the worst was 3.814494 ms, and no underruns were reported.

## Stress-test preparation

A one-minute dense stress smoke test completed 960/960 events with zero
scheduled drift and zero underruns. Callback interval error was 0.398600 ms p50,
1.118571 ms p95, 3.563273 ms p99, and 17.997518 ms maximum.

The first 30-minute attempt reached 21 minutes and 20,160 expected callbacks
before Android's automatic full-backup service attached to the debug package and
sent the instrumentation process `SIGKILL`. This was an environmental process
termination, not a test assertion or engine exception. Debug builds now disable
backup so the OS cannot invalidate subsequent stress runs this way.

## Completed 30-minute stress result

The backup-isolated rerun completed with the speaker volume at zero. The screen
turned off during the run; event delivery continued.

- 28,800/28,800 callbacks completed at 240 BPM with sixteenth subdivisions.
- Scheduled drift: 0.0 ms.
- Callback interval error: 0.379069 ms p50, 1.247355 ms p95, and 3.650024 ms p99.
- Maximum callback interval error: 32.399007 ms.
- `AudioTrack` underruns: 0.
- Rendered chunks: 720,001.
- Written frames: 86,400,120.

## Interpretation

This proves real-device decoding, callback progression, PCM rendering, and
polyrhythm event completeness. It does not measure the acoustic transient
leaving the speaker.

The initial locked-screen run's underrun did not recur in five unlocked-screen
runs. This is encouraging but does not yet establish causation or prove that
startup/locked-state underruns are harmless. The release gate still requires
explicit locked/unlocked lifecycle tests, loopback onset measurements,
longer one-hour validation, load/thermal tests, and zero missed beats.

The completed stress run closes the initial 30-minute engine stability check,
including screen-off operation within instrumentation. The 32.399007 ms maximum
callback outlier remains important even though scheduled drift was zero and
there were no underruns. It reinforces that callback arrival is not a
sample-frame or acoustic timing guarantee.
