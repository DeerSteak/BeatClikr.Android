# Phase 8 Pixel 8a Resource Profile — Invalid Collector Run

- Workload: fixed 240 BPM sixteenth notes for 60 minutes
- Build: minified benchmark application and benchmark instrumentation
- Device and route: Pixel 8a, Android 17, built-in speaker at media volume 6/25
- Display and power: screen on at brightness 128/255; AC powered; USB ADB only
- CPU collector: one device-side `simpleperf stat --app` process with ten-second cumulative reports
- Raw artifacts: `benchmarks/raw/phase8/20260804T151603Z-resource-steady-240-sixteenth-60m`

## Resource observations

The 3,470-second profiler population reported 17.38% mean CPU, 17.44% p50, 18.18% p95, 18.34% p99, and 18.65% maximum when adjacent cumulative task-clock reports were differenced. These observations are below TB-014's 25% mean and 35% p95 ceilings.

The warm snapshot reported 19,713 KiB PSS and 129,088 KiB RSS. The process was removed before the attempted final snapshot, so this run cannot establish TB-015 memory growth. Battery temperature changed from 30.9°C to 31.5°C and Android thermal status remained 0.

## Invalidity and playback result

The workload completed all 57,600 callbacks but failed its continuity assertions. Final diagnostics reported 14 application deadline misses, 14 dropped events, 11 platform underruns, 875 ms scheduled drift, 172,845,369 intended frames, and 172,803,120 rendered/written frames. The independently recorded first 20 minutes had no detectable missed or doubled acoustic events and passed its local interval-error distribution.

The principal difference from prior clean runs of the same workload was `simpleperf` ten-second reporting. The qualification protocol says collector-induced underruns invalidate the run, and the evidence does not prove causation; therefore this run is neither a TB-014/TB-015 acceptance pass nor a product continuity verdict. A replacement resource run must use aggregate-only CPU collection plus warm and near-end memory snapshots without periodic reports.
