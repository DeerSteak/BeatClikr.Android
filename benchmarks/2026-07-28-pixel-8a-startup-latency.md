# Pixel 8a Startup Latency

**Date:** 2026-07-28  
**Device:** Pixel 8a, Android 17  
**Build:** Debug instrumentation build  
**Route:** Built-in speaker  
**Workload:** 120 BPM, quarter notes  
**Samples:** 30 cold starts and 30 warm starts

## Method

`AudioStartupLatencyInstrumentedTest` measures from the monotonic timestamp immediately before `startMetronome` to the engine's predicted first-beat presentation timestamp. The latter includes the engine's estimated output latency. A cold sample creates a new engine and queues sound loading before start; a warm sample reuses one loaded engine.

The test records the current implementation as a Phase 0 baseline. It applies a 500 ms sanity limit but does not treat the Phase 1 acceptance budget as existing behavior.

## Results

| Start type | p50 | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Cold | 154.454 ms | 160.732 ms | 236.472 ms | 236.472 ms |
| Warm | 139.614 ms | 148.169 ms | 149.288 ms | 149.288 ms |

## Assessment

The proposed TB-007 local-route budget is p50 ≤ 75 ms, p95 ≤ 100 ms, and p99 ≤ 150 ms. Neither cold nor warm starts meet its p50 or p95 limits. Warm p99 meets the 150 ms limit; cold p99 does not.

The approximately 67 ms intentional first-beat delay and the current output-latency estimate account for much of the warm result. Cold starts add engine creation and sound preparation variability. Phase 3 render work and Phase 4 authoritative startup should revisit the budget and implementation using an actual presentation timestamp when the backend exposes one.

This result measures predicted presentation, not microphone-recorded time from tap to acoustic onset.
