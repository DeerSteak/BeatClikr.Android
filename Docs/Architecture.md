# Current Architecture

This document describes the implementation as it exists today. Deficiencies and planned changes live in [the architectural review](../ADVERSARIAL_PROJECT_REVIEW.md) and [the remediation plan](../BEATCLIKR_ACTION_PLAN.md).

## Application structure

BeatClikr is a single-module Kotlin application built with Jetpack Compose.

- `ui/` contains screens, reusable views, navigation, and ViewModels.
- `domain/` contains timing abstractions, session models, and use cases.
- `data/` contains Room, repositories, preferences, and file-backed data.
- `audio/` contains PCM decoding, scheduling, mixing, and `AudioTrack` output.
- `di/` wires the application graph with Hilt.

The intended dependency direction is UI to domain to data or audio. Android framework details should remain behind domain interfaces so timing and session logic can be tested without a device.

## Runtime ownership

`BeatClikrApplication` creates process-scoped dependencies. Activities and ViewModels own user-facing state; audio engines own active playback state and release native resources when playback stops.

Playback is intentionally foreground-only. The app stops active playback when it enters the background instead of maintaining a foreground media service. Background metronome playback is therefore not supported.

## State and persistence

Room stores playlists, songs, practice sessions, and related structured data. Preferences store lightweight app settings. Practice-day grouping uses the device's local date so history matches the calendar the musician sees.

Room schema version 4 is the migration baseline. Versions 1–3 were not distributed to the supported population and are reset if encountered. Future production schema changes must include exported schemas and a real migration from version 4 onward.

Proprietary samples are excluded from Git. A tracked requirements file defines their names and properties. CI creates non-proprietary placeholders with the same resource contract.

## Main execution flows

### Standard metronome

1. The Compose screen sends controls to `MetronomeViewModel`.
2. The ViewModel updates observable state and controls the audio engine.
3. The timing engine schedules beats against a monotonic clock.
4. The mixer writes PCM chunks to a streaming `AudioTrack`.
5. Beat callbacks update visuals and optional haptic or flash feedback.

### Polyrhythm

The polyrhythm flow has separate UI and timing state but uses the same output principles. Each rhythm advances independently against a shared monotonic time base, and coincident events are mixed into the output stream.

### Practice history

Completed activity is written through the repository to Room. Calendar and streak views read aggregated local-day results. UI code should not perform database queries or date-boundary calculations directly.

## Known architectural limits

- Scheduling and mixing use application-managed threads rather than a native real-time audio callback.
- Display, haptic, flash, and acoustic output each add independent latency.
- Emulator tests establish functional scheduling and decoding, not audible timing quality.
- Foreground-only playback is a product policy embedded in lifecycle handling.
- CI placeholders prove the public build path, not production sample quality.

These limits are tracked in the remediation plan rather than hidden in implementation comments.
