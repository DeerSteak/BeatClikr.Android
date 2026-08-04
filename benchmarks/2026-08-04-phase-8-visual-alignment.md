# Phase 8 Pixel 8a Visual Alignment Observation

- Workloads: standard metronome and flashlight at 120 BPM, plus polyrhythm at 120 BPM with a 6:4 ratio
- Display: Pixel 8a at 60 Hz
- Capture: high-speed video at 240 fps
- Direction before calibration: animation appeared ahead of recorded audio
- Observed uncorrected offset: approximately 12 camera frames, or 50 ms
- Calibration: repeated hand claps captured less than 12 inches from the camera showed a similar video-leading-audio offset

## Assessment

A 240 fps frame is 4.167 ms and a 60 Hz display refresh is 16.667 ms. The similar approximately 12-frame offset in the clap calibration indicates that the uncorrected metronome offset is dominated by the camera's audio/video synchronization. Sound propagation over less than 12 inches is below approximately 0.9 ms.

Subtracting the approximate clap offset leaves the standard, flashlight, and 6:4 polyrhythm residuals near zero. This is an observational TB-011 pass for steady standard and polyrhythm playback and an observational TB-013 pass for flashlight alignment on the Pixel 8a built-in-speaker route. The flashlight's raw audio lag was approximately 12 camera frames, matching the calibration offset. The approximate manual frame count, single observation, and uncalibrated consumer camera prevent a p95 or stronger precision claim. No separate physical retune-boundary alignment claim is made; TB-009 and TB-010 cover application/render boundary correctness.
