# Phase 8 Pixel 8a Screen-On Battery UI Stress

- Source: `1b7e1c0a0af2c128883162efe98fd0bc9024cfd4` plus the recorded working tree
- Device: Pixel 8a, Android 17 build `CP2A.260705.006`
- Build: minified benchmark application and benchmark instrumentation
- Route: built-in speaker, media volume 6/25
- Display: screen on, adaptive brightness disabled, brightness 128/255 during the workload
- Connectivity and power: Wi-Fi with one wireless ADB transport; AC, USB, and wireless charging false
- Workload: 60-minute UI stress starting at 240 BPM sixteenth notes, cycling 240→239→240 BPM and sixteenth→eighth→sixteenth, with periodic activity recreation
- Raw artifacts: `benchmarks/raw/phase8/20260804T122941Z-battery-ui-interaction-60m-run-1`

## Playback result

The test passed 1/1 in 3,610.715 seconds. It completed 721 UI cycles in session 1 with zero application deadline misses, drops, platform underruns, or underrun-skipped frames. Intended, rendered, and written counts were identical at 173,102,880 frames.

## Battery and thermal observation

| Measurement | Before | Manual after |
| --- | ---: | ---: |
| Displayed battery | 100% | 92% |
| Charge counter | 3,706,000 µAh | 3,322,000 µAh |
| Battery temperature | 29.5°C | 28.2°C |
| Android thermal status | 0 | 0 |

The wrapper's automatic post-run capture failed after the instrumentation result because the wrapper file was edited during execution. The manual snapshot occurred at 13:57:01Z, about 27 minutes after instrumentation ended, so the battery observation window is not matched to the workload. The delta is conservative screen-on stress characterization only; no normalized battery rate or TB-017 verdict is claimed. The test's playback assertions and saved instrumentation result remain valid.

## Restoration

Brightness mode 1, brightness 17, ten-minute timeout, and plugged-in keep-awake value 3 were restored manually. Media volume remained unchanged at 6/25. Future runs use the wrapper's versioned volume capture and restoration and must not modify the running wrapper.
