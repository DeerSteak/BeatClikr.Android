# Phase 9 Pixel 8a Qualification Summary

- Source behavior: three cycles on `45a0f7e`; two final cycles on `9dc819f`
- Device: Pixel 8a, Android 17 build `CP2A.260705.006`
- Build: minified benchmark application
- Route: built-in speaker
- ADB: one USB transport after explicitly disconnecting wireless

## Accepted short observation

One start-background-lock cycle passed. While locked, Android reported `PlaybackForegroundService` as a foreground service with `mediaPlayback` type, foreground ID 2001, one ongoing public transport notification, and one action. The BeatClikr media session was active with `PLAYING`, metadata “Metronome playing,” and actions value 3, representing pause and stop only.

Dispatching the pause system command while locked submitted the terminal stop behavior. The foreground service and BeatClikr media session disappeared, while the application process retained the same PID. There was no system control capable of starting or silently resuming playback.

Two additional semantic start-background-lock cycles passed on `45a0f7e`. A visible lock-screen Pause control ended one session directly. The other continued across unlock and ended from the visible notification-shade Pause control. Explicit restart created exactly one new foreground service and media session; each stop removed both while the application retained PID 1244. No stale control or automatic resume was observed.

Two more cycles passed after `9dc819f` corrected terminal `Interrupted` and `Failed` service lifetime. With credential locks removed, each session continued through background and screen-off in a `mediaPlayback` foreground service. The visible notification Pause control removed the service and media session without killing process PID 7274. Across five accepted cycles, no stale control, leaked active service, duplicate session, or automatic resume appeared.

## Excluded attempt

A scripted repeat attempt failed closed before playback began because the Pixel required its PIN after locking. The script observed no active service or media session and stopped immediately. It is not counted as a product failure or qualification cycle because the blind coordinate never reached BeatClikr.

## Remaining physical evidence

- Confirm audio continues while backgrounded and locked, while haptic and flashlight stop when BeatClikr is not visible.
- Confirm unlock does not resume a stopped phase and explicit restart creates one new service/session.
- Run the one-hour locked maximum-density workload and retain start/end service diagnostics, audio counters, service/process survival, and practice accounting.
- Compare matched foreground and locked foreground-service battery runs.
- Exercise route loss while backgrounded and recheck torch/haptic failsafes.

The device no longer has a credential lock, so remaining screen-off automation does not require user unlock assistance.
