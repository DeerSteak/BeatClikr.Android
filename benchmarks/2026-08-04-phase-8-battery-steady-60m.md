# Phase 8 Pixel 8a Steady-State Battery Qualification — Run 1

- Date and observation window: 2026-08-04 14:01:46Z–15:02:03Z (3,617 seconds)
- Source: `1b7e1c0a0af2c128883162efe98fd0bc9024cfd4` plus the recorded working tree
- Device: Pixel 8a, Android 17 build `CP2A.260705.006`
- Application APK SHA-256: `44514a98fd2f8cdf693491800d8ef7681c45f914bb1ea6ab0fc115a2ae674e7b`
- Test APK SHA-256: `2797215c680c65496ce3570aeff87ecc583ad9b6923741087168756312f203f7`
- Build: minified benchmark application and benchmark instrumentation
- ADB: one wireless transport, `192.168.50.64:45927`
- Power: AC false; USB false; wireless charging false
- Route: built-in speaker, media volume 6/25, unmuted
- Display: screen on, adaptive brightness off, brightness 128/255, two-hour timeout
- Connectivity: Wi-Fi and wireless debugging on
- Workload: fixed 240 BPM sixteenth notes for 60 minutes
- Raw artifacts: `benchmarks/raw/phase8/20260804T140142Z-battery-steady-240-sixteenth-60m-run-1`

## Battery and thermal results

| Measurement | Start | End | Normalized result |
| --- | ---: | ---: | ---: |
| Displayed battery level | 92% | 86% | 5.97 percentage points/hour |
| Charge counter | 3,312,000 µAh | 3,122,000 µAh | 189,107 µAh/hour; 5.71% of starting charge/hour |
| Battery temperature | 28.3°C | 29.8°C | +1.5°C |
| Android thermal status | 0 | 0 | No escalation |

This repetition passes TB-017's provisional ceiling of no more than six displayed percentage points per hour. The charge counter provides the more granular consumption measurement.

## Playback result

The test passed 1/1 in 3,600.394 seconds and produced all 57,600 expected events with zero scheduled drift, route changes, application deadline misses, drops, or platform underruns. Intended, rendered, and written counts were identical at 172,803,600 frames; the final timestamp reported 172,797,600 presented frames, one output-drain interval behind written output.

Callback interval error was 2.544 ms p50, 7.739 ms p95, 8.905 ms p99, and 75.010 ms maximum. The isolated maximum did not cause a deadline miss, dropped event, underrun, skipped frame, or scheduled drift.

## Exact command

```bash
ANDROID_SERIAL=192.168.50.64:45927 PHASE8_BRIGHTNESS=128 PHASE8_SCREEN_TIMEOUT_MS=7200000 PHASE8_MEDIA_VOLUME=6 ./tools/run_phase8_device_test.sh battery-steady-240-sixteenth-60m-run-1 -- --no-daemon :app:connectedBenchmarkAndroidTest -Pbeatclikr.testBuildType=benchmark -Pandroid.testInstrumentationRunnerArguments.class=com.bfunkstudios.beatclikr.AudioEngineStressInstrumentedTest -Pandroid.testInstrumentationRunnerArguments.stressDurationMinutes=60
```

## Restoration and qualification status

Adaptive brightness mode 1, brightness 17, ten-minute timeout, plugged-in keep-awake value 3, and media volume 6/25 were restored or preserved. This controlled steady-state run completes the amended one-run TB-017 release sniff check. It supports only the qualitative battery-efficiency claim, not a specific advertised battery-life duration.
