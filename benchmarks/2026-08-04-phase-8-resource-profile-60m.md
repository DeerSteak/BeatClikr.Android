# Phase 8 Pixel 8a Aggregate Resource Profile — 60 Minutes

- Source: `1b7e1c0a0af2c128883162efe98fd0bc9024cfd4` plus the recorded working tree
- Device: Pixel 8a, Android 17 build `CP2A.260705.006`
- Build: minified benchmark application and benchmark instrumentation
- Route: built-in speaker at media volume 5/25
- Display and power: screen on at brightness 128/255; AC powered; USB ADB only
- Workload: fixed 240 BPM sixteenth notes for 60 minutes
- CPU method: one device-side aggregate `simpleperf stat --app` interval; no periodic reports
- Memory method: one warm and one near-end `dumpsys meminfo` snapshot
- Raw artifacts: `benchmarks/raw/phase8/20260804T161853Z-resource-steady-240-sixteenth-60m-aggregate`

## Resource results

| Metric | Result | Budget | Verdict |
| --- | ---: | ---: | --- |
| Aggregate CPU | 17.54% of one core over 3,450.102 seconds | Mean ≤25% | Pass |
| Warm PSS | 17,727 KiB | — | — |
| Near-end PSS | 27,701 KiB | Growth ≤10,240 KiB | Pass at +9,974 KiB (+9.74 MiB) |
| Warm RSS | 127,176 KiB | Observational | — |
| Near-end RSS | 138,520 KiB | Observational | +11,344 KiB |
| Android thermal status | 0→0 | Below moderate | Pass |
| Battery temperature | 31.3→30.5°C | Observational | No increase |

The aggregate-only method establishes TB-014's clean one-hour mean but does not itself produce a percentile distribution. The invalid periodic-report run measured 18.18% p95, 18.34% p99, and 18.65% maximum; the earlier sampled profile measured 20.00% p95, 21.09% p99, and 24.10% maximum. Those populations are accepted only as corroborating CPU-distribution evidence because their polling invalidated audio continuity. Combined with the clean aggregate mean, they pass TB-014. PSS growth passes TB-015 narrowly, with 266 KiB of margin. TB-016 passes without thermal escalation.

## Playback result

The test passed 1/1 and completed all 57,600 expected events with zero scheduled drift, route changes, application deadline misses, or drops. It recorded one isolated platform `AudioTrack` underrun, zero evidence of an application recovery skip, 720,015 chunks, 172,803,600 rendered/written frames, and 172,797,606 estimated presented frames. The 21-frame difference between the end-boundary intended counter and rendered frames is below one output sample per event and did not alter the scheduled timeline.

Under the amended TB-008 policy, the isolated platform underrun is retained and classified rather than made an automatic failure because it caused no repeatable application defect, drop, deadline miss, recovery skip, or acoustic anomaly. The same workload's prior battery run was fully clean.

## Collector comparison

The preceding ten-second-reporting `simpleperf` run caused or coincided with 14 application deadline misses/drops and 11 platform underruns and is invalid. Removing periodic reports restored clean application continuity, supporting collector interference as the cause of that run's failure.
