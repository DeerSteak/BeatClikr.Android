# Pixel 8a Lower-Overhead Profile

**Date:** 2026-07-28  
**Device:** Pixel 8a, Android 17  
**Workload:** 240 BPM with sixteenth subdivisions for 30 minutes  
**CPU profiler:** Device-side `simpleperf stat --app` for 1,500.101 seconds  
**Other sampling:** One start snapshot and one delayed end snapshot

## Audio result

The instrumentation workload completed all 28,800 expected events before a temporary USB disconnect interrupted Gradle's post-test artifact collection.

| Metric | Result |
| --- | ---: |
| Scheduled drift | 0 ms |
| Callback interval error p50 | 0.364 ms |
| Callback interval error p95 | 1.141 ms |
| Callback interval error p99 | 1.789 ms |
| Callback interval error maximum | 67.165 ms |
| `AudioTrack` underruns | 0 |
| Rendered chunks | 719,917 |
| Written frames | 86,390,040 |

The test itself completed without an assertion failure. Gradle failed afterward because the USB cable was briefly disconnected while the test infrastructure was retrieving artifacts and uninstalling test packages.

## Aggregate CPU result

| Metric | Result |
| --- | ---: |
| Profile duration | 1,500.101 s |
| Task clock | 314,101.123 ms |
| Average CPU use | 0.209 cores |
| Equivalent single-core percentage | 20.94% |
| Context switches | 1,254,864 |
| Page faults | 6,703 |

The aggregate CPU result meets the proposed TB-014 mean limit of 25% of one core. This run did not collect CPU percentiles, so it does not establish the proposed p95 limit.

## Memory and thermal observations

The start snapshot reported 155,909 KiB total proportional set size and 261,416 KiB total resident set size. The process had exited before the delayed post-reconnect memory snapshot, so this run does not independently establish memory growth. The earlier intrusive profile showed flat memory; the unplugged battery run will capture another start/end pair without repeated sampling.

Android thermal status was 0 in the delayed end snapshot. Because the USB disconnect delayed that snapshot, it is supporting evidence rather than a precise end-of-workload temperature.

## Assessment

Unlike the earlier ten-second diagnostic loop, this run used aggregate device-side performance counters and did not invoke `dumpsys` repeatedly. It completed with zero underruns. That strongly indicates the previous 182 underruns were induced by intrusive diagnostic sampling rather than ordinary maximum-density playback.

The raw start, profiler, stress-log, and delayed end artifacts are stored under `benchmarks/raw/2026-07-28-pixel-8a-low-overhead/`.
