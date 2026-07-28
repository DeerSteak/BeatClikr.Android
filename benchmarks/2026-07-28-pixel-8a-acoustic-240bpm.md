# Pixel 8a Acoustic Baseline — 240 BPM Sixteenths

**Date:** 2026-07-28  
**Source commit:** `7e14f4503a1a01d5d28478d151ef8216d8309ec9`  
**Device:** Pixel 8a, Android 17  
**Route:** Built-in speaker to microphone/audio interface  
**Tempo:** 240 BPM  
**Subdivision:** Sixteenth notes, nominal 62.5 ms interval  
**Recording:** Stereo 16-bit PCM, 44.1 kHz, 123.857 seconds  
**Recording SHA-256:** `3812041982e3d90fb68c3eb5e6c63d90742c4c2c2231ccabca5ba863b7363066`

The source WAV is local and is not committed.

## Method

Transient energy was measured from the first difference of both channels with a
1.5 ms RMS envelope. Local maxima were constrained to at least 45 ms apart.
Thresholds from 0.003 through 0.03 full scale all produced the same 1,982 onset
count, indicating that the recorded background noise did not create
threshold-sensitive detections.

Beat and subdivision samples have different acoustic attack shapes. Adjacent
strongest-peak positions therefore include a repeatable timbre offset and are
not a defensible direct scheduler-jitter measure. The primary interval analysis
compares like positions every four subdivisions, then expresses the 250 ms
same-position error per subdivision.

## Results

- Detected onsets: 1,982.
- Missing or extra onsets: none observed.
- Fitted quarter-note interval: 249.994877 ms.
- Fitted tempo: 240.004918 BPM.
- End-to-end fitted drift versus exactly 240 BPM: −2.535848 ms.
- Equivalent per-subdivision absolute interval error:
  - p50: 0.000000 ms;
  - p95: 2.494331 ms;
  - p99: 2.505669 ms;
  - maximum: 5.000000 ms.

## Interpretation

This is the first measurement of sound leaving the Pixel 8a speaker. It shows no
audible-event loss and very small average tempo error across approximately two
minutes. The result supports the user's observation that playback sounded
reasonably steady.

The percentile errors are conservative because they still include microphone,
room reflection, sample attack-shape, interface clock, and detector uncertainty.
They do not measure absolute tap-to-sound latency. A longer recording, a
single-identical click sound for every subdivision, and a saved analysis tool
would produce a stronger regression baseline.

