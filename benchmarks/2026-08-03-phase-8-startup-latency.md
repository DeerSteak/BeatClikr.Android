# Phase 8 Pixel 8a Startup Latency

- Date: 2026-08-03
- Source: `4cf4ac24817acc32fdbeb5753b4fe3f3adce2dec` plus the recorded Phase 8 working tree
- Device: Pixel 8a (`akita`), one USB ADB transport
- OS: Android 17, `CP2A.260705.006` / `15641320`
- Build: minified `benchmark`, debug-signed, production sounds
- Route: built-in speaker
- Screen: on, awake, and unlocked; stay-awake setting restored after each run
- Population: 30 cold and 30 warm engine starts
- Method: accepted engine request to predicted first-event presentation, correlated from `AudioTimestamp` in the `System.nanoTime()` monotonic clock domain

## Valid result

| Population | p50 | p95 | p99 | Maximum | TB-007 |
| --- | ---: | ---: | ---: | ---: | --- |
| Cold | 197.812 ms | 234.587 ms | 376.680 ms | 376.680 ms | Fail p50, p95, p99 |
| Warm | 187.609 ms | 208.689 ms | 212.207 ms | 212.207 ms | Fail p50 |

TB-007 requires p50 ≤ 175 ms, p95 ≤ 225 ms, and p99 ≤ 300 ms. This run is release-blocking unless the implementation improves and a matched rerun passes, or the contract is explicitly amended before release. The test retains the `release/4.1.0` 67 ms first-beat delay.

## Static latency decomposition

The obtained 48 kHz stream reports a 240-frame burst and 2,886-frame buffer. The app's conservative `(buffer + burst) / sample rate` estimate is 65.125 ms, and the 4.1.0 first-beat pre-roll is 67 ms. Those fixed components total 132.125 ms before stream creation, focus acquisition, command dispatch, and platform/device overhead. Relative to the warm p50 of 187.609 ms, the remaining observed median is approximately 55.484 ms; this is an inference, not an independently timestamped decomposition.

The backend requests a two-burst buffer, but `AudioTrack.getMinBufferSize()` raises the obtained capacity to 2,886 frames on this Pixel. Reducing below the platform minimum is not a safe paper optimization, and reusing or pre-opening the stream would change lifecycle/resource behavior that requires its own contract and long-run validation. No production timing parameter is changed by this report. A second corrected distribution should establish measurement repeatability before either implementation work or a TB-007 amendment.

## Invalid precursor runs

The original instrumentation subtracted `SystemClock.elapsedRealtimeNanos()` from a relative intended frame and allowed negative results through a one-sided maximum assertion. Its large negative distributions are invalid. A first correction used timestamp correlation but still mixed the suspend-inclusive elapsed clock with the audio timestamp's monotonic clock; the new nonnegative assertion rejected it. These failures led to the final same-clock method and stronger percentile assertions.

## Artifacts

- Valid run: `benchmarks/raw/phase8/20260803T185321Z-startup-built-in-clock-fixed/`
- Relative-frame invalid run: `benchmarks/raw/phase8/20260803T184755Z-startup-built-in/`
- Mixed-clock rejected run: `benchmarks/raw/phase8/20260803T185125Z-startup-built-in-fixed-rerun/`
- R8 interoperability failure: `benchmarks/raw/phase8/20260803T185028Z-startup-built-in-fixed/`
