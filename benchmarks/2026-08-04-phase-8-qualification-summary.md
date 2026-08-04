# Phase 8 Pixel 8a Qualification Summary

Phase 8 qualified the benchmark build on a Pixel 8a running Android 17, using the built-in speaker unless noted. The completed evidence is the TB-018 baseline for the next timing- or resource-sensitive change. The pre-Phase-3 `release/4.1.0` comparator remains historical context.

## Timing and render

- TB-001 through TB-003 passed the permanent 12-hour multi-rate scheduler, coincidence, and exhaustive stall suites.
- One-hour maximum-density standard, representative polyrhythm, and 720-cycle UI-interaction runs completed with zero application deadline misses, drops, duplicate events, catch-up output, mixed configurations, or incorrect recovery.
- TB-009 and TB-010 passed pure and render boundary tests. Physical retune alignment is intentionally unclaimed.
- Corrected built-in-speaker startup populations passed the amended TB-007 limits of 250 ms p50, 300 ms p95, and 500 ms maximum.
- Platform `AudioTrack` underruns are classified under TB-008 rather than treated as automatic failures. Controlled render and acoustic evidence showed no repeatable application defect or detectable acoustic consequence.

## Acoustic evidence

Low, typical, and maximum-density recordings used a PreSonus AudioBox, Shure MV7, GarageBand at 44.1 kHz/16-bit, and approximately two-inch microphone placement. Proprietary recordings remain outside the repository.

| Workload | Events | Fitted BPM | Absolute interval error | Misses/doubles |
| --- | ---: | ---: | --- | ---: |
| 30 BPM quarter notes | 588 | 29.999 | 1.995 ms p95, 2.948 ms p99/max | 0/0 |
| 120 BPM eighth notes | 4,632 classified | 119.995987 | 0.975 ms p95/p99 | 0/0 |
| 240 BPM sixteenth notes | 19,264 | 240.005795 | 0.062 ms p95, 0.357 ms p99, 2.500 ms max | 0/0 |

Application frame drift passed TB-001. Physical fitted drift remains observational because the recording interface clock was not independently calibrated. An earlier 120 BPM recording contained one approximately 10 ms phase step near 15 minutes; synchronized reruns did not reproduce an application fault.

## Alignment and lifecycle

- Clap-calibrated 240 fps video at a 60 Hz display rate placed steady 120 BPM standard and 6:4 polyrhythm visual residuals near zero. The raw approximately 12-frame video-leading-audio offset matched the camera calibration.
- The flashlight showed the same approximately 12-frame raw camera offset and passed TB-013 observationally after calibration. A single manual capture does not establish p95.
- A 99.564-second desk-coupled recording contained 196 click/rattle pairs. Haptic-onset p95 ranged from 13.220 to 17.959 ms across rise thresholds, passing the 25 ms TB-012 ceiling observationally. This measured audible desk rattle rather than the motor with a direct sensor.
- Competing-media focus, transient and permanent focus loss, focus release, Bluetooth warning/removal/explicit restart, and torch failsafes passed physical observation.
- The built-in speaker is the qualified physical route. Absolute Bluetooth, USB, analog line, and headphone latency is route-dependent and unclaimed. Application-frame correctness and automated route-loss behavior remain in scope.

## Resource and battery

- The clean 3,450-second aggregate profile averaged 17.54% of one core. Diagnostic populations measured 18.18% and 20.00% CPU p95, both below the 35% ceiling; their intrusive polling invalidated continuity claims but not CPU characterization.
- Warm-to-near-end PSS growth was 9.74 MiB against the 10 MiB TB-015 ceiling.
- Android thermal status remained 0.
- The controlled one-hour, screen-on, built-in-speaker battery sniff check consumed 5.97 displayed percentage points per hour against the six-point TB-017 ceiling. This supports the qualitative “battery efficient” claim, not a generalized battery-life promise.

## Regression policy and limitations

The completed Phase 8 results are the current baseline. A future timing- or resource-sensitive change receives one matched sniff check; repeat only an anomaly, near-budget result, or suspected operational regression. Fixed-budget violations block immediately, while noisy regressions require confirmation.

The qualification does not generalize Pixel 8a speaker measurements to other Android devices or routes. Physical USB, analog, and Bluetooth latency, direct-sensor haptic onset, calibrated recorder-clock drift, and physical retune-boundary alignment remain outside the fixed release claims. Offline resampling screening does not establish unmeasured physical-route behavior.
