# Capabilities and Background Behavior

## Shipping capabilities

BeatClikr currently provides:

- instant metronome with subdivisions, odd-meter accents, tap tempo, and ramping;
- configurable acoustic and synthetic sound banks;
- M-against-N polyrhythms;
- song library, ordered playlists, and Focus Mode;
- practice history, streak sharing, and daily reminders;
- optional vibration, camera flash, mute, dark theme, and keep-awake behavior;
- compact phone and expanded tablet navigation;
- offline local operation without an account.

The supported platform floor is Android 12/API 31. CI exercises an API 31 phone, an API 36 phone, and an API 36 tablet; Android 17/API 37 compatibility remains a manual check until its CI emulator is stable.

## Android permissions and features

| Declaration | Purpose |
| --- | --- |
| `POST_NOTIFICATIONS` | Practice reminders on supported Android versions |
| `RECEIVE_BOOT_COMPLETED` | Restore enabled reminder scheduling after reboot |
| `VIBRATE` | Optional beat feedback |
| Optional camera flash feature | Detect and use torch feedback when available |
| `FileProvider` | Share generated streak-card files safely |

The camera flash is optional, so devices without one remain installable.

## Background behavior

An already active metronome session continues under ordinary backgrounding and device lock through a media-playback foreground service. Backgrounding never starts playback. Haptic and flashlight effects remain foreground-only, and the keep-awake preference remains limited to a visible Activity. The persistent notification and lock-screen media session expose terminal pause/stop controls only; they cannot start, resume, seek, skip, or change playback speed. Reminder alarms and receivers operate independently.

Playback remains the primary audio experience and requests long-duration audio focus. There is no backing-track coexistence mode; one would require separately approved behavior and testing.

## Storage and backup

Room and SharedPreferences store user-authored data locally. Backup uses an allowlist for the database and preferences; generated PCM, transient diagnostics, caches, and files are excluded. The concise offline and no-tracking policy is published in [Privacy.md](Privacy.md). Proprietary WAV files and signing material are never committed.

## Release validation

Public CI validates source and resource wiring with generated non-production tones. An authorized production build must validate the proprietary sounds, produce the bundle, and pass emulator plus Pixel 8a checks. See [Validation.md](Validation.md) and [PlaybackPerformance.md](PlaybackPerformance.md).
