# Phase 9 Pixel 8a Qualification Summary

- Source behavior: `6d8ce31` plus the docs-only `3d238f3`
- Device: Pixel 8a, Android 17 build `CP2A.260705.006`
- Build: minified benchmark application
- Route: built-in speaker
- ADB: one USB transport after explicitly disconnecting wireless

## Accepted short observation

One start-background-lock cycle passed. While locked, Android reported `PlaybackForegroundService` as a foreground service with `mediaPlayback` type, foreground ID 2001, one ongoing public transport notification, and one action. The BeatClikr media session was active with `PLAYING`, metadata “Metronome playing,” and actions value 3, representing pause and stop only.

Dispatching the pause system command while locked submitted the terminal stop behavior. The foreground service and BeatClikr media session disappeared, while the application process retained the same PID. There was no system control capable of starting or silently resuming playback.

## Excluded attempt

A scripted repeat attempt failed closed before playback began because the Pixel required its PIN after locking. The script observed no active service or media session and stopped immediately. It is not counted as a product failure or qualification cycle because the blind coordinate never reached BeatClikr.

## Remaining physical evidence

- Repeat at least five unlock/start/background/lock/system-stop cycles with semantic or user-confirmed start input.
- Confirm audio continues while backgrounded and locked, while haptic and flashlight stop when BeatClikr is not visible.
- Confirm unlock does not resume a stopped phase and explicit restart creates one new service/session.
- Run the one-hour locked maximum-density workload and retain start/end service diagnostics, audio counters, service/process survival, and practice accounting.
- Compare matched foreground and locked foreground-service battery runs.
- Exercise route loss while backgrounded and recheck torch/haptic failsafes.

The phone currently requires its user PIN. ADB cannot unlock it, so the remaining steps require the product owner to unlock the device before automation can continue.
