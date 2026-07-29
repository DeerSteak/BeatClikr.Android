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

The supported platform floor is Android 12/API 31. CI exercises that minimum and the Android 16/API 36 target; Android 17/API 37 compatibility remains a manual check until its CI emulator is stable.

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

Metronome playback is foreground-only. Audio and flashlight resources stop when the app leaves the foreground; there is no foreground service, media session, lock-screen control, or background audio promise. Reminder alarms and receivers operate independently of playback.

## Storage and backup

Room and SharedPreferences store user-authored data locally. Generated PCM is regenerable internal data and should be excluded from backup. The current backup classification still requires verification and a concise privacy statement. Proprietary WAV files and signing material are never committed.

## Release validation

Public CI validates source and resource wiring with generated non-production tones. An authorized production build must validate the proprietary sounds, produce the bundle, and pass emulator plus Pixel 8a checks. See [Validation.md](Validation.md) and [PlaybackPerformance.md](PlaybackPerformance.md).
