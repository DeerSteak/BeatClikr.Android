# Phase 8 Pixel 8a 30 BPM Quarter-Note Diagnostic

- Workload: benchmark engine at 30 BPM with quarter notes for 20 minutes
- Device route: Pixel 8a built-in speaker, media volume 16/25
- Recording: GarageBand through PreSonus AudioBox and Shure MV7, approximately two inches from the speaker
- Format: stereo 44.1 kHz, 16-bit PCM WAV; 1,176.970068 seconds
- WAV SHA-256: `ca89d2e6e4044f64be34f55d287bee1841d28bb12554ccfd4d3a0d1d1f33ca62`
- Raw artifacts: `benchmarks/raw/phase8/20260804T212113Z-acoustic-diagnostic-30bpm-quarter-20m`

## Phone diagnostics

The benchmark passed 1/1 with all 600 expected events, zero scheduled drift, route changes, deadline misses, drops, or platform underruns. Intended, rendered, and written counts were identical at 57,603,600 frames. Callback error was 0.089 ms p50, 1.292 ms p95, 1.961 ms p99, and 4.091 ms maximum; callback arrival is not the acoustic clock.

## Acoustic analysis

Thresholds 0.01, 0.02, 0.03, and 0.05 detected the same 588 onsets with identical metrics. Threshold 0.005 added one low-level noise transient and was excluded. Across every classified stable four-beat window:

| Metric | Result |
| --- | ---: |
| Detected onsets | 588 |
| Minimum rolling four-beat tempo | 29.988185 BPM |
| Maximum rolling four-beat tempo | 30.009867 BPM |
| Windows below 29.5 BPM | 0 |
| Windows above 30.5 BPM | 0 |
| Fitted tempo | 29.999000 BPM |
| Fitted endpoint error | +39.152 ms |
| Acoustic interval error p50 | 0.068 ms |
| Acoustic interval error p95 | 1.995 ms |
| Acoustic interval error p99 | 2.948 ms |
| Acoustic interval error maximum | 2.948 ms |

There is no acoustic evidence of a missed, doubled, fast, or slow four-beat period. The local interval distribution passes TB-004 and TB-005. Under the amended TB-006 contract, application frame drift remains governed by TB-001 and the uncalibrated AudioBox/GarageBand endpoint error is observational.

The WAV contains no full-scale samples. Its file-wide peak is −0.10 dBFS, so the capture is hot but not digitally clipped.
