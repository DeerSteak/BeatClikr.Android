# Phase 8 Qualification-Test Audit

## Authority and review rule

Expected behavior comes from approved Android contracts and `origin/release/4.1.0`; iOS fills only silent areas. A test is acceptable when its expected result is independently derived, it can fail for the claimed regression, and it measures the same evidence layer as the claim.

## Findings

| Test or tool | Valid claim | Invalid promotion or assumption | Disposition |
| --- | --- | --- | --- |
| `PureCoreQualificationTest` | Exact frame arithmetic, duplicate prevention, stall recovery, 4.1.0 standard phase preservation, polyrhythm restart, and command atomicity | It does not exercise Android rendering or physical output | Retain as TB-001–TB-003 and TB-009–TB-010 gate |
| `AudioEngineStressInstrumentedTest` | Intended/rendered/written equality, obtained stream facts, render cost, deadline/drop counters, timestamp availability, and `AudioTrack` underruns | Callback arrival error is not acoustic jitter; intended-frame nanosecond conversion is not presentation time | Keep callback values diagnostic and base acceptance on frame/counter invariants |
| `AudioEngineInstrumentedTest` | Bounded emulator/device smoke behavior and production-sound decoding | Its allowed-drop calculation after an underrun cannot pass TB-008; 100 ms callback p95 is not a product budget | Retain as a smoke test only |
| `AudioStartupLatencyInstrumentedTest` | Request-to-predicted-presentation latency when `AudioTimestamp` is available | The former test mixed relative frames and absolute clocks, allowed negative values, and later mixed suspend-inclusive and audio monotonic clocks | Corrected to timestamp correlation, `System.nanoTime()`, nonnegative values, and direct TB-007 percentile assertions |
| `RenderedEventTestSession` | Polls immutable records from the production render-event ring | Poll time is not render time or acoustic presentation | Use intended frames and attached correlation, never poll arrival, for timing claims |
| `tools/analyze_acoustic_onsets.py` | Reproducible microphone-recording onset count, fitted drift, and like-timbre interval errors | Detector output alone cannot identify whether an anomaly came from the app, transport, room, recorder clock, or threshold choice | Require threshold sweep, manual anomaly review, and recorded setup |
| `tools/profile_android_process.sh` | Host-sampled CPU, PSS, RSS, battery temperature, and thermal state | Repeated `dumpsys` can induce underruns and cannot qualify normal use | Use only for explicitly intrusive characterization; use low-overhead aggregate collection for acceptance |
| `PlaybackUiStressInstrumentedTest` | Accessibility-driven controls, same-session commits, Activity recreation, frame accounting, deadline/drop counters, and recovery accounting in the minified benchmark build | Compose test runtime and a separate Hilt test component require target keep rules that distort the optimized APK | Keep it in `benchmarkAndroidTest`; use the normal app runner, external UI automation, and a primitive benchmark-only diagnostics facade |

## Required follow-ups

- Keep invalid and failed startup artifacts; do not reuse their distributions.
- Add 44.1 and 48 kHz production-sound decode coverage before closing resampling qualification.
- Add representative standard and polyrhythm one-hour render workloads instead of generalizing the maximum-density standard run.
- Define one UI workload compatible with 4.1.0 navigation semantics: top-level navigation intentionally stops playback and is tested separately, not performed during a continuous-playback gate.
- Record whether every reported start is a new engine instance, a prewarmed engine, or a process launch; do not call an engine restart an application cold launch.
- Require zero application drops, mixed configurations, catch-up events, and incorrect recovery. Record platform underruns and classify recurrence and acoustic impact under TB-008 instead of assuming every count is application-caused.
