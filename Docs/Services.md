# Services

Services isolate audio, device feedback, reminder scheduling, and repository behavior from Compose UI.

## Audio services

`PlaybackCoordinator` implements the intent-only application command port `IAudioPlayerService` and read-only `PlaybackObservation`. Callers submit typed `PlaybackIntent` values; UI and secondary outputs observe transport state and renderer-originated `committedEvents`.

`MetronomeAudioEngine` implements `PlaybackEnginePort`. Every coordinator start/stop entry point requires session identity, so stale owners cannot issue sessionless teardown. Engine timing delegates remain below that port to prompt committed-event draining.

`MetronomeAudioEngine` manages audio focus, one-shot visual scheduling, frame-audio publication, and the polyrhythm visual engine. `PolyrhythmTimingEngine` advances two visual rhythms on a shared monotonic timeline. `FrameAudioEngine` owns prepared sound selection and frame-session publication; `AudioTrackFrameSession` drives the streaming backend. `PcmFileCache` reads versioned generated PCM while `SoundBankPreparer` decodes and validates Android raw resources on the control context.

Prepared sound banks are immutable snapshots keyed by sound bank and `SoundFile`. A complete replacement is published atomically only after every required resource succeeds. WAV decoding preserves leading silence, downmixes supported channel layouts to mono, resamples before publication, and returns typed missing, corrupt, empty, or incompatible failures.

Sound preparation APIs perform blocking resource and cache work and may only be called from the serialized metronome control context. The engine retains its complete required-sound set across bank switches and preserves the last usable waveform snapshot on preparation failure. Requested configuration, confirmed audible bank/sound identities, and typed degradation remain separately readable in authoritative application state. `PreparedPcmWaveform.copySamples()` is a publication-time operation and must never be called per render block.

Playback requests long-duration audio focus with `AUDIOFOCUS_GAIN`. Focus denial fails startup, every focus-loss class stops the active session without automatic resume, and teardown abandons a held focus lease. Backing-track coexistence is not guaranteed; reliable metronome ownership takes priority over mixing with another media application.

The active `AudioTrack` observes routing changes while the engine also observes audio-device additions and removals. A committed route change interrupts the current session, closes its stream, and requires an explicit restart; that restart opens a new stream and rebuilds rendering from the new route's obtained properties. Bluetooth routes are exposed to UI projections as latency-variable.

`FrameAudioMetricsSnapshot` preserves its existing field schema for instrumentation compatibility, but role counts now come from successfully rendered frame events rather than enqueue operations.

Audio events are positioned by absolute frames inside render blocks. Secondary-output callbacks use one-shot monotonic scheduling and drop expired intervals rather than producing catch-up bursts.

## Secondary output services

- `HapticFeedbackService` emits optional vibration feedback and supports explicit cancellation.
- `FlashlightService` checks for torch support and controls the camera flash.
- `SecondaryOutputCoordinator` consumes session-guarded committed audio events, schedules presentation-relative effects, and exposes retained typed failures independently of audio transport. Scheduler and hardware failures are isolated; torch pulses retain bounded off recovery, and process `STARTED` visibility gates foreground-only effects.

Haptic and torch effects are foreground-only. Torch-on is paired with a 40 ms pulse-off and a separate 250 ms failsafe; stop, interruption, failure, and lifecycle exit invalidate pending work and force both outputs off. Platform and hardware latency remain independent, so these effects are not evidence of acoustic alignment.

## Reminder services

`PracticeReminderScheduler` owns notification permission-aware scheduling and rescheduling. `PracticeReminderBootReceiver` restores schedules after reboot, and `PracticeReminderNotificationReceiver` posts reminder notifications. `PracticeReminderBodyCalculator` selects copy from projected streak state.

## Repositories and preferences

Song, playlist, and practice repositories adapt Room DAOs into flows and suspending mutations. `AppPreferences` implements `IAppPreferences` over SharedPreferences. Playlist/history mutations are transactional, and preferences use bounded versioned codecs.

## Lifecycle policy

Playback is foreground-only. A `ProcessLifecycleOwner` stop tears down playback and secondary effects after the process leaves the foreground; an individual Activity stop only clears that window's keep-awake flag. Configuration recreation therefore preserves an active session, while backgrounding or screen-off stops it. No playback foreground service, media session, or media notification exists.
