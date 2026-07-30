# Current Architecture

This document describes the implementation as it exists today. Deficiencies and planned changes live in [the architectural review](../ADVERSARIAL_PROJECT_REVIEW.md) and [the remediation plan](../BEATCLIKR_ACTION_PLAN.md).

## Application structure

BeatClikr is a single-module Kotlin application built with Jetpack Compose.

- `ui/` contains screens, reusable views, navigation, and ViewModels.
- `music/` contains Android-free exact musical values, immutable configurations, and frame-event vocabulary.
- `data/` contains Room, repositories, preferences, and file-backed data.
- `services/` contains PCM decoding, scheduling, mixing, `AudioTrack` output, and platform integrations.
- `di/` wires the application graph with Hilt.

The `music/` package is a dependency leaf and cannot depend on Android classes, clocks, resources, audio objects, persistence, or presentation models. Its configuration layer contains exact standard and polyrhythm inputs, session origins, monotonic event identity, and frame-event vocabulary. `StandardMetronomeTimeline` now provides the first pure frame-range scheduler, while production playback remains on the characterized engine until the controlled Phase 3 integration.

### Music integration boundary

The music domain uses `require()` to enforce internal value invariants. External values and commands must be constructed and reduced through `PlaybackInputBoundary` on the control path before any work is handed to a renderer. `IllegalArgumentException` from a rejected domain invariant becomes `PlaybackInputFailure.InvalidDomainInput`; it must be recorded and mapped to coordinator state or user-facing recovery rather than thrown on the render or audio thread. Unexpected implementation failures are not reclassified as input errors.

`AudioRenderBackend` is the platform-output boundary for Phase 3. It owns stream open/start/render/stop operations, exposes obtained stream properties and presentation timestamps, and reports typed failures through a registered sink. `AudioTrackRenderBackend` is the first implementation and reports the stream's obtained sample rate, channel count, burst, buffer, and performance mode. Render buffers and timestamp holders are caller-owned so later implementations can reuse them on the real-time path.

`FramePcmRenderer` requests each absolute output range through the visitor implemented by the Phase 2 timelines, mixes prepared mono waveforms at their exact frame offsets, and retains unfinished voices across contiguous blocks. A fixed voice table and reusable integer accumulator keep the render call allocation-free; the final conversion saturates only after every overlapping voice is summed. Stop, restart, and discontinuity recovery reset retained voices, while any partial render failure produces a silent block.

The backend's channel adapter duplicates each mono frame across the obtained channel layout in a reusable buffer. Frame offsets and return values remain measured in frames, so stereo interleaving cannot change duration or event position.

`FrameAudioStreamOwner` opens the backend before publishing a renderer, allowing the renderer timeline to use the obtained sample rate and the render block to use the obtained burst size. The published renderer owns one immutable waveform binding for that stream. The owner tracks backend start separately from render-loop liveness, advances absolute frame ownership through complete and partial writes, resets retained waveform tails at every start, failure, resync, and stop boundary, and makes stop idempotent. Render failures halt the loop until an explicit resync or stream replacement; resync requires a previously started backend and never arms an unopened track. Rejected operations report to the sink registered for the responsible call.

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
