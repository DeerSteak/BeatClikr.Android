# Services

Services isolate audio, device feedback, reminder scheduling, and repository behavior from Compose UI.

## Audio services

`AudioPlayerService` is the application-facing implementation of `IAudioPlayerService`. It owns `MetronomeAudioEngine`, forwards delegate events, and exposes standard and polyrhythm setup, start, stop, sound-bank, and metrics operations.

`MetronomeAudioEngine` manages audio focus, timing threads, standard beat scheduling, and the polyrhythm engine. `PolyrhythmTimingEngine` advances two rhythms on a shared monotonic timeline. `AudioTrackEngine` mixes cached mono PCM samples into a streaming `AudioTrack`; `PcmFileCache` decodes Android raw resources into persistent internal PCM files.

The current scheduler is polling-based and does not place events at exact sample offsets within render blocks. See [PlaybackPerformance.md](PlaybackPerformance.md) and the [adversarial review](../ADVERSARIAL_PROJECT_REVIEW.md) for the replacement requirements.

## Secondary output services

- `HapticFeedbackService` emits optional vibration feedback.
- `FlashlightService` checks for torch support and controls the camera flash.

Both consume scheduled beat events but have independent platform and hardware latency. Neither should be used as evidence of acoustic alignment.

## Reminder services

`PracticeReminderScheduler` owns notification permission-aware scheduling and rescheduling. `PracticeReminderBootReceiver` restores schedules after reboot, and `PracticeReminderNotificationReceiver` posts reminder notifications. `PracticeReminderBodyCalculator` selects copy from projected streak state.

## Repositories and preferences

Song, playlist, and practice repositories adapt Room DAOs into flows and suspending mutations. `AppPreferences` implements `IAppPreferences` over SharedPreferences. Transactional playlist/history mutations and safer versioned preference codecs remain planned hardening work.

## Lifecycle policy

Playback is foreground-only. Process and activity lifecycle handling stop audio when the app leaves the foreground; no foreground playback service or media notification exists.
