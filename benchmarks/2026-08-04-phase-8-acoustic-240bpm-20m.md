# Phase 8 Pixel 8a Maximum-Density Acoustic Recording

- Recording duration: 1,205.205533 seconds
- Workload: fixed 240 BPM sixteenth notes; expected interval 62.5 ms
- Phone route: Pixel 8a built-in speaker, media volume 6/25
- Phone display: screen on, adaptive brightness off, brightness 128/255
- Recorder: GarageBand through a PreSonus AudioBox and Shure MV7
- Microphone placement: approximately 2 inches from the phone speaker
- Format: stereo 44.1 kHz, 16-bit PCM WAV
- WAV SHA-256: `507aba8fb98b5ca8ef4f3dd85ac282adc1f73e909c43f0bf98152cca03c779d2`
- Raw WAV and analysis: `benchmarks/raw/phase8/20260804T151603Z-resource-steady-240-sixteenth-60m/acoustic`

## Onset analysis

The versioned analyzer used a 1.5 ms first-difference RMS window, 45 ms minimum onset distance, 62.5 ms intended interval, and grouping 4 to compare like timbres. Thresholds 0.003, 0.005, 0.010, 0.020, and 0.030 all produced identical results.

| Metric | Result |
| --- | ---: |
| Detected onsets | 19,264 |
| Fitted interval | 62.498491 ms |
| Fitted tempo | 240.005795 BPM |
| Fitted endpoint error over detected population | −29.067 ms |
| Absolute inter-onset error p50 | 0.011 ms |
| Absolute inter-onset error p95 | 0.062 ms |
| Absolute inter-onset error p99 | 0.357 ms |
| Absolute inter-onset error maximum | 2.500 ms |

The stable count across a tenfold threshold range and the maximum grouped error below 10 ms show no detector evidence of a missed or doubled event. This maximum-density population passes TB-004 and TB-005 for its 20-minute segment.

## Clock limitation

The fitted endpoint error scales to approximately −86.8 ms/hour when the uncalibrated AudioBox/GarageBand sample clock is treated as exact. The extremely low local interval-error distribution and clean application frame accounting are consistent with recorder-clock offset, but that is an inference rather than proof. Under the amended TB-006 contract, application frame drift remains governed by TB-001 and this physical fitted drift is observational.

This recording completes the maximum-density 20-minute acoustic population. The 30 BPM quarter-note and 120 BPM eighth-note populations remain.
