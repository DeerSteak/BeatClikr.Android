# Phase 8 polyrhythm render qualification

**Date:** 2026-08-03  
**Device:** Pixel 8a, Android 17, build `CP2A.260705.006`  
**Build:** Minified benchmark, release-equivalent  
**Route:** Built-in speaker, 48 kHz mono, low-latency `AudioTrack`  
**Screen:** On and awake throughout both runs

The tests compare every rendered event frame, voice role, role index, and coincidence with the independent `PolyrhythmContractFixture`. They require zero application deadline misses and drops, exact written-frame accounting, and exact accounting across any platform-underrun recovery skip.

| Workload | Event frames | Beat voices | Rhythm voices | Deadlines | Drops | Platform underruns | Recovery skips | Intended/rendered/written |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 5:7 at 120 BPM, 60 minutes | 11,314 | 7,200 | 5,143 | 0 | 0 | 0 | 0 | 172,803,600 / 172,803,600 / 172,803,600 |
| 15:14 at 240 BPM, 60 minutes | 28,800 | 14,400 | 15,429 | 0 | 0 | 1 | 0 | 172,803,600 / 172,803,600 / 172,803,600 |

Both application workloads pass. The dense run's single platform-reported underrun had no correlated skipped frame, application deadline miss, dropped event, or accounting discontinuity. It is retained as a TB-008 anomaly and does not establish an acoustic pass or prove that the counter increment was inaudible.

Raw artifacts:

- `benchmarks/raw/phase8/20260803T234148Z-polyrhythm-5x7-120bpm-60m`
- `benchmarks/raw/phase8/20260804T004757Z-polyrhythm-15x14-240bpm-60m`
