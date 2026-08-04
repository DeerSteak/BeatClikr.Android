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

The built-in speaker is the qualified Phase 8 physical route. USB audio and analog line/headphone output retain the application-frame requirements, but their end-to-end presentation latency is hardware-dependent and unclaimed because no suitable phone-connected adapter, DAC, or analog-output hardware was available. Wired and USB results require their own named route measurement before publication.

## Acceptance budgets

| ID | Metric | Local-route budget | Bluetooth budget | Minimum evidence |
| --- | --- | --- | --- | --- |
| TB-001 | Long-run scheduler drift | At most one output sample after 12 simulated hours | Same | Pure scheduler test at every supported sample rate and representative fractional periods |
| TB-002 | Duplicate intended event frames | Zero, except deliberate coincident polyrhythm events represented as one mixed frame | Same | Property and long-run scheduler tests |
| TB-003 | Catch-up events after a stall | Zero | Same | Multi-event stall tests |
| TB-004 | Missed or doubled acoustic events | Zero classified misses or doubles over at least 20 minutes each at low, typical, and maximum density | Application-generated misses or doubles: zero; transport artifacts recorded separately | Automated onset classification and manual anomaly review, backed by a clean one-hour maximum-density render run |
| TB-005 | Absolute acoustic inter-onset error | p50 ≤ 1 ms, p95 ≤ 3 ms, p99 ≤ 5 ms, maximum ≤ 10 ms | No end-to-end percentile promise | At least one hour across low, typical, and maximum supported event density |
| TB-006 | Fitted acoustic drift | Application frame drift must meet TB-001; physical fitted drift is observational without a calibrated reference clock | Same | Regression against intended onset series with recorder clock and calibration status disclosed |
| TB-007 | Start latency | p50 ≤ 250 ms, p95 ≤ 300 ms, maximum ≤ 500 ms from accepted play intent to predicted local-route presentation | Report distribution without a fixed gate | At least 30 cold and 30 warm starts per route |
| TB-008 | Render continuity | Zero application deadline misses, dropped or duplicate events, mixed configurations, or incorrect recovery. Platform-reported underruns must not be repeatable under controlled normal use, cause a detectable acoustic timing defect, or materially regress against matched evidence. | Same application invariants; transport underruns recorded separately | Android stream counters, app diagnostics, repeated runs, and acoustic review when an underrun occurs |
| TB-009 | Tempo-change boundary | The pending next event remains on the pre-change boundary; subsequent event frames use the new tempo with ≤ one-sample application error | Same application-frame requirement | Boundary fixtures across every groove and odd-meter mode |
| TB-010 | Configuration atomicity | Zero mixed old/new configurations at one boundary | Same | Randomized command-sequence tests |
| TB-011 | Visual alignment | p95 onset within one display refresh interval of predicted audio presentation | Observational only unless route calibration is available | High-speed video or synchronized instrumentation |
| TB-012 | Haptic alignment | p95 onset within 25 ms of predicted audio presentation on the reference device | Observational only | External sensor or synchronized recording |
| TB-013 | Flash alignment | p95 onset within 35 ms of predicted audio presentation on the reference device | Observational only | Photodiode or high-speed video |
| TB-014 | Sustained CPU | Mean ≤ 25% and p95 ≤ 35% of one performance core during maximum-density audio-only playback | Same | One-hour low-overhead aggregate profile for the mean, corroborated by sampled diagnostic percentile populations; intrusive populations cannot qualify continuity |
| TB-015 | Memory stability | Proportional set size growth ≤ 10 MiB over one hour after warm-up | Same | Start, warm-up, periodic, and final samples |
| TB-016 | Thermal behavior | Android thermal status remains below moderate during the one-hour audio-only reference run | Same | Platform thermal status log |
| TB-017 | Battery consumption | ≤ 6 percentage points per hour at documented reference settings | Report separately | One one-hour release sniff check with brightness, route, volume, radio state, and battery health recorded |
| TB-018 | Matched-baseline regression | The qualified Phase 8 release is the current baseline. A future timing- or resource-sensitive change receives one matched sniff check; repeat only an anomaly, near-budget result, or suspected operational regression. | Same application metrics; transport latency remains observational | Identical settings and workloads against the latest qualified baseline; fixed-budget violations or confirmed regressions block release |

## Normal and stress conditions

The normal-use gate runs a release build with diagnostics buffered in memory, built-in speaker or a declared local route, a stable screen, and ordinary control interaction. It excludes continuous `dumpsys`, debugger attachment, profiler sampling, and other host activity known to perturb the audio process.

The UI-interaction stress gate repeatedly changes playback controls and recreates the Activity while maximum-density playback continues. A separate hostile-diagnostic run may deliberately induce contention; its failures remain hardening evidence but do not replace or invalidate the normal-use gate. An isolated platform underrun is recorded as an anomaly rather than an automatic release failure; recurrence, clustering, incorrect recovery, or a detectable acoustic defect requires investigation.

## Current evidence

The initial two-minute Pixel 8a speaker recording at 240 BPM with sixteenth-note subdivisions produced 1,982 detected onsets, no missing or extra events, 240.004918 fitted BPM, −2.535848 ms fitted endpoint drift, p95 absolute inter-onset error of 2.494331 ms, p99 of 2.505669 ms, and a 5 ms maximum. Phase 8 supersedes that short evidence with low-, typical-, and maximum-density acoustic populations plus clean one-hour maximum-density render evidence.

The 30-minute Pixel scheduler stress run completed 28,800 callbacks with zero scheduled drift and underruns. Its callback p95 was 1.247355 ms and p99 was 3.650024 ms. Callback timing is supporting evidence and must not be reported as acoustic timing.

The CPU profile averaged 15.41% of one core, used approximately 149.74 MiB proportional set size without observed growth, and did not escalate thermal status. Intrusive ten-second diagnostic sampling induced 182 underruns, so a lower-overhead one-hour run is still required for TB-008 and TB-014 through TB-017.

The lower-overhead 30-minute profile used aggregate device-side performance counters and completed with zero underruns, zero scheduled drift, and average CPU use of 20.94% of one core. The unplugged battery observation consumed 2.84 displayed percentage points per hour, or 5.45% of its starting charge counter per hour, with no thermal escalation. Its one-hour audio workload recorded four underruns; that historical result remains anomaly evidence but lacks the recovery and acoustic detail needed for a current TB-008 verdict.

The current one-hour low-overhead profile establishes a 17.54% aggregate CPU mean. Two separately sampled diagnostic populations reported p95/p99/max values of 18.18%/18.34%/18.65% and 20.00%/21.09%/24.10%. Their polling interfered with audio continuity, so they are accepted only as corroborating CPU-distribution evidence and cannot qualify TB-008. Together with the clean aggregate run, they pass TB-014.

The pinned pre-Phase-3 release-equivalent comparator remains historical context. Its 30-minute resource run averaged 14.67% of one core, showed no PSS growth or thermal escalation, and completed with zero underruns. Its one-hour unplugged run consumed 2.87 displayed percentage points per hour, or 5.54% of its starting charge counter per hour, remained at thermal status 0, and completed 57,600 maximum-density events with zero scheduled drift and zero underruns. The completed Phase 8 qualification supersedes it as the baseline for the next timing- or resource-sensitive change.

The current 2026-08-03 screen-on release-equivalent maximum-density standard run completed 57,600 events with zero scheduled drift, deadline misses, drops, route changes, underruns, or intended/rendered/written frame mismatch. It passes the standard render portion of TB-008; representative polyrhythm and UI-interaction stress evidence remain required.

The TB-007 gate includes the deliberate pre-roll needed to prepare and commit the first event. Earlier tighter percentile gates contradicted that design. The 2026-07-28 comparator's startup calculation was later found to mix a relative intended frame with an absolute clock, so its distribution remains invalid. On 2026-08-03, the product owner amended TB-007 to p50 ≤ 250 ms, p95 ≤ 300 ms, and maximum ≤ 500 ms. The corrected same-clock release-equivalent run passes the amended gate; see `benchmarks/2026-08-03-phase-8-startup-latency.md`.

## Reporting rules

Every result records commit, build variant, device, OS build, audio route, sample rate, buffer configuration, tempo, groove or ratio, duration, measurement method, and raw-artifact location.

A percentile claim names its population and measurement layer. No callback statistic is described as acoustic onset, and no local-route result is generalized to Bluetooth.

Fixed ceilings are release limits, not permission to consume unused margin. A timing-sensitive change receives one TB-018 sniff check against the last accepted matched baseline. A fixed-budget violation blocks release immediately; an anomalous nonzero delta is repeated before it is classified as an operational regression.
