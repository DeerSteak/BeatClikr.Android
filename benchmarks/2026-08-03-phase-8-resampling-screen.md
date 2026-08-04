# Phase 8 Production-Sound Resampling Screen

- Date: 2026-08-03
- Source: `4cf4ac28768e8e88495034a81f4eb03f8308fde8` plus the recorded Phase 8 working tree
- Assets: 30 production sounds plus the documented unused silence resource
- Source format: 44.1 kHz PCM WAV
- Targets: 44.1 and 48 kHz
- Method: offline characterization mirroring the production decoder's linear interpolation
- Raw CSV: `benchmarks/raw/phase8/offline-resampling/2026-08-03-resampling.csv`

## Results

Same-rate 44.1 kHz decoding produced zero modeled onset shift, peak change, crest change, derivative change, and round-trip error. For 44.1→48 kHz conversion across non-silence assets:

- maximum absolute modeled onset shift: 0.075 ms (`crashr_a3.wav`);
- maximum absolute peak change: 0.328 dB (`crashr_a3.wav`);
- largest round-trip normalized error: 0.0474 (`synth_hatopen_asharp2.wav`);
- largest derivative-RMS reduction among the five greatest onset-shift cases: 26.4% (`crashr_a3.wav`).

The onset shift is well below one millisecond and does not itself fail TB-004 through TB-006. Linear interpolation measurably softens some high-frequency transients, so this offline screen does not close subjective transient quality. Listening and recorded-device comparison remain required before deciding whether higher-quality offline resampling or per-rate assets are necessary.
