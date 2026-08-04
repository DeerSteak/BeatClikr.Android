# Phase 8 physical-test handoff

Use the Pixel 8a on Android 17 with the screen on unless the step explicitly requires another state. Record the route, volume, brightness, connectivity, and whether the observation was audible, visual, or counter-only. Restore changed settings afterward.

## Product-owner decisions

- [x] TB-007 amended to p50 ≤ 250 ms, p95 ≤ 300 ms, and maximum ≤ 500 ms; the corrected current distributions pass.
- [x] Amend TB-017 to one controlled one-hour sniff check; no stronger quantitative battery-life claim is planned.
- [x] Amend TB-018 so Phase 8 becomes the current baseline and future sensitive changes receive one matched sniff check with confirmation only for anomalies.

## Physical lifecycle checks

- [x] Start a common media player, start BeatClikr, and confirm the expected pause or duck behavior.
- [x] Cause transient and permanent audio-focus loss; confirm BeatClikr stops and does not silently resume.
- [x] Stop BeatClikr and confirm the other media player can recover after focus release.
- [x] Connect Bluetooth, verify the latency warning, start playback, remove the route, and confirm stop plus explicit restart.
- [x] Record wired and USB physical latency as unmeasured because suitable phone-connected output hardware was unavailable; retain automated route-loss coverage.
- [x] Verify the torch turns off after explicit stop, app backgrounding, interruption, and forced failure where practical.

## External timing evidence

- [x] Record the low-, typical-, and maximum-density acoustic populations at 30 BPM quarter notes, 120 BPM eighth notes, and 240 BPM sixteenth notes.
- [x] Record the 240 BPM WAV checksum, interface, sample rate, route, volume, brightness, microphone distance, and detected event count.
- [x] Limit the physical alignment claim to steady standard and 120 BPM 6:4 polyrhythm playback at 60 Hz; make no separate retune-boundary claim.
- [x] Accept the 196-event desk-rattle capture as observational TB-012 evidence and the clap-calibrated 240 fps flashlight capture as observational TB-013 evidence, with their measurement limits disclosed.

## Resource evidence

- [x] Run one current one-hour low-overhead CPU, memory, and thermal workload; the clean aggregate mean and corroborating diagnostic percentile populations pass TB-014.
- [x] Treat the automated UI run as screen-on battery stress characterization, not a TB-017 repetition.
- [x] Complete the TB-017 sniff check at fixed 240 BPM sixteenth notes, brightness 128/255, volume 6/25, built-in speaker, Wi-Fi on, and no external power.
- [x] Establish the completed Phase 8 evidence as the TB-018 baseline; no retroactive `release/4.1.0` campaign is required.

Return the recordings and observations with their setup notes. The versioned analysis tools can calculate onset counts, fitted drift, interval-error percentiles, and threshold sensitivity without repeating the physical capture.
