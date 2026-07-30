# Services

Services isolate audio, device feedback, reminder scheduling, and repository behavior from Compose UI.

## Audio services

`AudioPlayerService` is the application-facing implementation of `IAudioPlayerService`. It owns `MetronomeAudioEngine`, forwards delegate events, and exposes standard and polyrhythm setup, start, stop, sound-bank, and metrics operations.

`MetronomeAudioEngine` manages audio focus, one-shot visual scheduling, frame-audio publication, and the polyrhythm visual engine. `PolyrhythmTimingEngine` advances two visual rhythms on a shared monotonic timeline. `FrameAudioEngine` owns prepared sound selection and frame-session publication; `AudioTrackFrameSession` drives the streaming backend. `PcmFileCache` reads versioned generated PCM while `SoundBankPreparer` decodes and validates Android raw resources on the control context.

Prepared sound banks are immutable snapshots keyed by sound bank and `SoundFile`. A complete replacement is published atomically only after every required resource succeeds. WAV decoding preserves leading silence, downmixes supported channel layouts to mono, resamples before publication, and returns typed missing, corrupt, empty, or incompatible failures.

Sound preparation APIs perform blocking resource and cache work and may only be called from the serialized metronome control context. The engine retains its complete required-sound set across bank switches and preserves the last usable waveform snapshot on preparation failure. Requested configuration, actual audible bank/sound identities, and the typed failure remain separately readable until Phase 4 makes them authoritative application state. `PreparedPcmWaveform.copySamples()` is a publication-time operation and must never be called per render block.

Playback requests long-duration audio focus with `AUDIOFOCUS_GAIN`. Focus denial fails startup, every focus-loss class stops the active session without automatic resume, and teardown abandons a held focus lease. Backing-track coexistence is not guaranteed; reliable metronome ownership takes priority over mixing with another media application.

The active `AudioTrack` observes routing changes while the engine also observes audio-device additions and removals. A committed route change interrupts the current session, closes its stream, and requires an explicit restart; that restart opens a new stream and rebuilds rendering from the new route's obtained properties. Bluetooth routes are exposed to UI projections as latency-variable.

`FrameAudioMetricsSnapshot` preserves its existing field schema for instrumentation compatibility, but role counts now come from successfully rendered frame events rather than enqueue operations.

Audio events are positioned by absolute frames inside render blocks. Secondary-output callbacks use one-shot monotonic scheduling and drop expired intervals rather than producing catch-up bursts.

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
