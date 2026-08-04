# Phase 9 Physical Handoff

Use the Pixel 8a on Android 17 with exactly one USB ADB transport. Unlock the phone with its PIN before each sequence that returns to the app. The benchmark build must be rebuilt and reinstalled after the `bb8d2c9` diagnostics commit.

## Repeated lifecycle sequence

Repeat five times:

1. Open BeatClikr and explicitly start the metronome.
2. Confirm one foreground service, one BeatClikr media session, and continuing audio after Home.
3. Lock the phone and confirm audio continues while haptic and flashlight remain off.
4. Use notification or lock-screen pause/stop and confirm audio ends.
5. Unlock and reopen BeatClikr; confirm there is no silent resume and no stale control.
6. Explicitly start again and confirm exactly one new service/session.

After each stop, `dumpsys activity services com.bfunkstudios.beatclikr.benchmark` must show no playback service, and `dumpsys media_session` must show no BeatClikr session. The app process may remain alive.

## Locked one-hour run

Use the built-in speaker at the documented reference settings and start 240 BPM sixteenth notes while visible. Background and lock the device, then capture the service diagnostics at the start and after one hour with:

```bash
adb shell dumpsys activity service \
  com.bfunkstudios.beatclikr.benchmark/.services.PlaybackForegroundService
```

The final snapshot must retain the same session, `Playing` transport, advancing and internally consistent frame/event counters, zero application deadline misses or drops, no incorrect underrun recovery, and no backend failure. Confirm the service and process survived and practice duration advanced across the locked interval. Stop from the system control and verify explicit restart remains required.

## Battery comparison

Run matched foreground-visible and locked-background workloads with the same starting charge range, duration, tempo, subdivisions, volume, route, radios, and charger state. Report the foreground-service delta without converting one Pixel observation into a generalized battery-life claim.

## Route and secondary-output checks

During active background audio, remove the available Bluetooth route and require one terminal stop plus explicit restart. With vibration and flashlight enabled, confirm both operate while visible, turn off on Home/lock, remain off during background audio, and recover only after the app is visible again. Repeat explicit stop, interruption, and forced torch-failure failsafes where practical.
