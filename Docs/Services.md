# Services

Services isolate audio, device feedback, reminder scheduling, and repository behavior from Compose UI.

## Audio services

`AudioPlayerService` is the application-facing implementation of `IAudioPlayerService`. It owns `MetronomeAudioEngine`, forwards delegate events, and exposes standard and polyrhythm setup, start, stop, sound-bank, and metrics operations.

`MetronomeAudioEngine` manages audio focus, timing threads, standard beat scheduling, and the polyrhythm engine. `PolyrhythmTimingEngine` advances two rhythms on a shared monotonic timeline. `AudioTrackEngine` mixes prepared mono PCM samples into a streaming `AudioTrack`; `PcmFileCache` reads versioned generated PCM while `SoundBankPreparer` decodes and validates Android raw resources on the control context.

Prepared sound banks are immutable snapshots keyed by sound bank and `SoundFile`. A complete replacement is published atomically only after every required resource succeeds. WAV decoding preserves leading silence, downmixes supported channel layouts to mono, resamples before publication, and returns typed missing, corrupt, empty, or incompatible failures.

Sound preparation APIs perform blocking resource and cache work and may only be called from the serialized metronome control context. The engine retains its complete required-sound set across bank switches and preserves the last usable waveform snapshot on preparation failure. Requested configuration, actual audible bank/sound identities, and the typed failure remain separately readable until Phase 4 makes them authoritative application state. `PreparedPcmWaveform.copySamples()` is a publication-time operation and must never be called per render block.

`AudioTrackMetricsSnapshot` reports aggregate queued clicks and separate beat/rhythm enqueue counts so contract tests can verify sound roles without depending on Android resource IDs or inspecting proprietary waveforms.

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
