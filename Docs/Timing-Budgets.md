# Timing and Resource Budgets

**Status:** Accepted initial acceptance budgets
**Date:** 2026-07-28  
**Reference device:** Pixel 8a running Android 17

## Purpose

These budgets turn the Phase 1 contracts into release gates. They are intentionally separated by measurement layer because scheduler frames, Android callbacks, electrical output, and microphone-recorded acoustic onsets do not prove the same thing.

## Test classes

| Class | Meaning |
| --- | --- |
| Pure scheduler | Deterministic frame generation without Android threads or audio hardware |
| Render path | Intended and rendered frame positions inside the application |
| Local acoustic | Microphone or loopback onset measurements for built-in speaker, wired, or USB routes |
| Bluetooth | Application correctness with device-dependent transport latency explicitly excluded |

## Acceptance budgets

| ID | Metric | Local-route budget | Bluetooth budget | Minimum evidence |
| --- | --- | --- | --- | --- |
| TB-001 | Long-run scheduler drift | At most one output sample after 12 simulated hours | Same | Pure scheduler test at every supported sample rate and representative fractional periods |
| TB-002 | Duplicate intended event frames | Zero, except deliberate coincident polyrhythm events represented as one mixed frame | Same | Property and long-run scheduler tests |
| TB-003 | Catch-up events after a stall | Zero | Same | Multi-event stall tests |
| TB-004 | Missed or doubled acoustic events | Zero over a continuous one-hour dense test | Application-generated misses or doubles: zero; transport artifacts recorded separately | Automated onset classification plus manual anomaly review |
| TB-005 | Absolute acoustic inter-onset error | p50 ≤ 1 ms, p95 ≤ 3 ms, p99 ≤ 5 ms, maximum ≤ 10 ms | No end-to-end percentile promise | At least one hour across low, typical, and maximum supported event density |
| TB-006 | Fitted acoustic drift | Absolute fitted endpoint error ≤ 5 ms per hour | Application frame drift must meet TB-001; acoustic transport drift is observational | Regression against intended onset series |
| TB-007 | Start latency | p50 ≤ 175 ms, p95 ≤ 225 ms, p99 ≤ 300 ms from accepted play intent to predicted local-route presentation | Report distribution without a fixed gate | At least 30 cold and 30 warm starts per route |
| TB-008 | Render underruns | Zero during a one-hour normal-use test and zero during the defined UI-interaction stress test | Zero application-reported stream underruns | Android stream counters and app diagnostics |
| TB-009 | Tempo-change boundary | After the command is accepted and its new restart origin is committed, the first rendered frame differs from that origin by ≤ one sample | Same application-frame requirement | Boundary fixtures across every groove and odd-meter mode |
| TB-010 | Configuration atomicity | Zero mixed old/new configurations at one boundary | Same | Randomized command-sequence tests |
| TB-011 | Visual alignment | p95 onset within one display refresh interval of predicted audio presentation | Observational only unless route calibration is available | High-speed video or synchronized instrumentation |
| TB-012 | Haptic alignment | p95 onset within 25 ms of predicted audio presentation on the reference device | Observational only | External sensor or synchronized recording |
| TB-013 | Flash alignment | p95 onset within 35 ms of predicted audio presentation on the reference device | Observational only | Photodiode or high-speed video |
| TB-014 | Sustained CPU | Mean ≤ 25% and p95 ≤ 35% of one performance core during maximum-density audio-only playback | Same | One-hour profile without intrusive polling |
| TB-015 | Memory stability | Proportional set size growth ≤ 10 MiB over one hour after warm-up | Same | Start, warm-up, periodic, and final samples |
| TB-016 | Thermal behavior | Android thermal status remains below moderate during the one-hour audio-only reference run | Same | Platform thermal status log |
| TB-017 | Battery consumption | ≤ 6 percentage points per hour at documented reference settings; provisional until a repeatable baseline is captured | Report separately | Three one-hour runs with brightness, route, volume, radio state, and battery health recorded |
| TB-018 | Matched-baseline regression | No statistically or operationally meaningful regression in startup latency, underruns, drift, onset error, CPU, memory, thermal behavior, or battery use even when the fixed ceiling still passes | Same application metrics; transport latency remains observational | Matched before/after release builds under identical settings, with at least three runs for noisy resource metrics |

## Normal and stress conditions

The normal-use gate runs a release build with diagnostics buffered in memory, built-in speaker or a declared local route, a stable screen, and ordinary control interaction. It excludes continuous `dumpsys`, debugger attachment, profiler sampling, and other host activity known to perturb the audio process.

The UI-interaction stress gate repeatedly changes screens and nonmusical controls while maximum-density playback continues. A separate hostile-diagnostic run may deliberately induce contention; its failures remain hardening evidence but do not replace or invalidate the normal-use gate.

## Current evidence

The two-minute Pixel 8a speaker recording at 240 BPM with sixteenth-note subdivisions produced 1,982 detected onsets, no missing or extra events, 240.004918 fitted BPM, −2.535848 ms fitted endpoint drift, p95 absolute inter-onset error of 2.494331 ms, p99 of 2.505669 ms, and a 5 ms maximum. It satisfies TB-004 and TB-005 for that short single-condition run but does not satisfy their required one-hour coverage.

The 30-minute Pixel scheduler stress run completed 28,800 callbacks with zero scheduled drift and underruns. Its callback p95 was 1.247355 ms and p99 was 3.650024 ms. Callback timing is supporting evidence and must not be reported as acoustic timing.

The CPU profile averaged 15.41% of one core, used approximately 149.74 MiB proportional set size without observed growth, and did not escalate thermal status. Intrusive ten-second diagnostic sampling induced 182 underruns, so a lower-overhead one-hour run is still required for TB-008 and TB-014 through TB-017.

The lower-overhead 30-minute profile used aggregate device-side performance counters and completed with zero underruns, zero scheduled drift, and average CPU use of 20.94% of one core. The unplugged battery observation consumed 2.84 displayed percentage points per hour, or 5.45% of its starting charge counter per hour, with no thermal escalation. Its one-hour audio workload recorded four underruns, so TB-008 remains unmet even though the provisional battery budget passes.

The TB-007 gate includes the deliberate pre-roll needed to prepare and commit the first event. The earlier 75/100/150 ms gate contradicted that design and the measured Pixel 8a baseline. The revised gate remains meaningfully tighter than a perceptibly slow start while admitting the measured cold distribution of p50 154.454 ms, p95 160.732 ms, and p99 236.472 ms and warm distribution of p50 139.614 ms, p95 148.169 ms, and p99 149.288 ms. Those debug-build results fit the accepted limits, but a release-build acceptance run must prove them independently.

## Reporting rules

Every result records commit, build variant, device, OS build, audio route, sample rate, buffer configuration, tempo, groove or ratio, duration, measurement method, and raw-artifact location.

A percentile claim names its population and measurement layer. No callback statistic is described as acoustic onset, and no local-route result is generalized to Bluetooth.

Fixed ceilings are release limits, not permission to consume unused margin. A timing-sensitive change must also pass TB-018 against the last accepted matched baseline. Measurement noise is handled with repeated runs and distributions rather than declaring any single nonzero delta a regression.
