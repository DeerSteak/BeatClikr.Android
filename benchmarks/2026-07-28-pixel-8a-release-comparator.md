# Pixel 8a Release Comparator

> **Startup correction (2026-08-03):** the startup calculation below mixed a relative intended frame with an absolute clock and is invalid. Do not use its startup distribution as TB-007 evidence. The corrected same-clock `AudioTimestamp` result is recorded in `benchmarks/2026-08-03-phase-8-startup-latency.md`. Other comparator measurements are unaffected by this correction.

**Date:** 2026-07-28  
**Source:** `df07bdd65a0801e0f102289620ea3efd16f31b9d` plus comparator-harness-only changes  
**Device:** Pixel 8a, Android 17, build `CP2A.260705.006`  
**Build:** Minified and resource-shrunk `benchmark`, non-debuggable and profileable by shell  
**Route:** Built-in speaker at media index 5/25 for startup and plugged resource runs, and 9/25 for the battery run

## Purpose

This pins the pre-Phase-3 TB-018 comparator. The `benchmark` build type inherits the production `release` build, uses a separate application ID and local debug signature for installation, and exposes shell profiling without making the APK debuggable. Its additional shrinker rules preserve only runtime and test-facing symbols needed by the external instrumentation APK while allowing the metronome engine APIs to remain optimized.

The production scheduler, renderer, sound selection, and product behavior are unchanged. Main and release playback sources are exactly those in the recorded commit.

## Startup reference

`AudioStartupLatencyInstrumentedTest` ran 30 cold and 30 warm starts at 120 BPM with quarter notes.

| Start type | p50 | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Cold | 163.881 ms | 170.821 ms | 172.758 ms | 172.758 ms |
| Warm | 159.486 ms | 166.582 ms | 168.837 ms | 168.837 ms |

All samples passed the 500 ms test sanity limit and the accepted TB-007 local-route limits.

## Short underrun reference

The five-minute maximum-density workload ran at 240 BPM with sixteenth subdivisions.

| Metric | Result |
| --- | ---: |
| Events | 4,800 |
| Scheduled drift | 0 ms |
| Callback interval error p50 | 0.432 ms |
| Callback interval error p95 | 1.470 ms |
| Callback interval error p99 | 5.069 ms |
| Callback interval error maximum | 21.411 ms |
| `AudioTrack` underruns | 0 |
| Rendered chunks | 119,969 |
| Written frames | 14,396,280 |

## Resource reference

The 30-minute maximum-density workload used a single 25-minute device-side `simpleperf stat --app` interval and one warm start and near-end resource snapshot.

| Metric | Result |
| --- | ---: |
| Aggregate CPU | 14.67% of one core |
| CPU profile duration | 1,500.120 s |
| Context switches | 1,303,520 |
| Page faults | 8,064 |
| Start PSS | 16.96 MiB |
| Near-end PSS | 12.15 MiB |
| PSS growth | −4.80 MiB |
| Start RSS | 120.21 MiB |
| Near-end RSS | 110.89 MiB |
| Android thermal status | 0 at start and near end |
| HAL battery temperature | 27.9°C to 29.4°C |
| HAL virtual-skin temperature | 28.7°C to 30.9°C |

The mean CPU result passes TB-014's 25% limit. This aggregate profile does not establish the p95 CPU limit. Memory did not grow and passes the available 30-minute TB-015 reference, while the release contract still requires the later one-hour qualification. Thermal status remained below moderate and passes this 30-minute TB-016 reference.

The audio workload completed all 28,800 events with zero scheduled drift and zero underruns. Callback interval error was 0.407 ms p50, 1.215 ms p95, 3.099 ms p99, and 445.458 ms maximum. The large callback-arrival outlier did not alter the authoritative scheduled timeline or cause an audio underrun, but it remains useful comparator evidence.

## Battery reference

The Pixel ran physically unplugged over wireless debugging for a 3,759-second observation window. The release-equivalent workload ran for one hour at 240 BPM with sixteenth subdivisions. The display remained awake at manual brightness 128/255, the built-in speaker used media index 9/25, and normal Wi-Fi and cellular radios remained enabled.

| Metric | Start | End | Normalized result |
| --- | ---: | ---: | ---: |
| Android battery level | 100% | 97% | 2.87 percentage points/hour |
| Charge counter | 3,944,000 µAh | 3,716,000 µAh | 218.36 mAh/hour |
| Charge-counter fraction | — | — | 5.54% of starting charge/hour |
| Battery temperature | 26.7°C | 28.4°C | +1.7°C |
| Android thermal status | 0 | 0 | No escalation |

The battery result passes TB-017's provisional six-percentage-point-per-hour limit. It is the first release-equivalent reference run; the contract still calls for three qualification repetitions.

The workload completed all 57,600 events with zero scheduled drift and zero `AudioTrack` underruns. Callback interval error was 0.466 ms p50, 1.977 ms p95, 4.457 ms p99, and 47.027 ms maximum. This improves on the earlier debug instrumentation battery run's four underruns and pins zero as the pre-Phase-3 release comparator.

After collection, adaptive brightness, brightness value 15, the two-minute screen timeout, and persisted speaker media index 5 were restored.

## Reproduction

Exact source, device, settings, workloads, and commands are stored in `benchmarks/raw/2026-07-28-pixel-8a-release-comparator/device-and-protocol.txt`. Raw logs and start/end resource snapshots are stored in the same directory.
