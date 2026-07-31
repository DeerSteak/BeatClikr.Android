# Current Architecture

This document describes the implementation as it exists today.

## Application structure

BeatClikr is a single-module Kotlin application built with Jetpack Compose.

- `ui/` contains screens, reusable views, navigation, and ViewModels.
- `music/` contains Android-free exact musical values, immutable configurations, and frame-event vocabulary.
- `data/` contains Room, repositories, preferences, and file-backed data.
- `services/` contains PCM decoding, scheduling, mixing, `AudioTrack` output, and platform integrations.
- `di/` wires the application graph with Hilt.

`PlaybackCoordinator` is the application-scoped transport authority. Playback ViewModels submit commands through `IAudioPlayerService` and project the coordinator's read-only transport state and committed renderer events; they do not install engine delegates or maintain independent playback truth.

The `music/` package is a dependency leaf and cannot depend on Android classes, clocks, resources, audio objects, persistence, or presentation models. Its configuration layer contains exact standard and polyrhythm inputs, session origins, monotonic event identity, and frame-event vocabulary. `StandardMetronomeTimeline` now provides the first pure frame-range scheduler, while production playback remains on the characterized engine until the controlled Phase 3 integration.

### Music integration boundary

The music domain uses `require()` to enforce internal value invariants. External values and commands must be constructed and reduced through `PlaybackInputBoundary` on the control path before any work is handed to a renderer. `IllegalArgumentException` from a rejected domain invariant becomes `PlaybackInputFailure.InvalidDomainInput`; it must be recorded and mapped to coordinator state or user-facing recovery rather than thrown on the render or audio thread. Unexpected implementation failures are not reclassified as input errors.

`AudioRenderBackend` is the platform-output boundary for Phase 3. It owns stream open/start/render/stop operations, exposes obtained stream properties and presentation timestamps, and reports typed failures through a registered sink. `AudioTrackRenderBackend` is the first implementation and reports the stream's obtained sample rate, channel count, burst, buffer, and performance mode. Render buffers and timestamp holders are caller-owned so later implementations can reuse them on the real-time path.

`FramePcmRenderer` requests each absolute output range through the visitor implemented by the Phase 2 timelines, mixes prepared mono waveforms at their exact frame offsets, and retains unfinished voices across contiguous blocks. A fixed voice table and reusable integer accumulator keep the render call allocation-free; the final conversion saturates only after every overlapping voice is summed. Stop, restart, and discontinuity recovery reset retained voices, while any partial render failure produces a silent block.

The backend's channel adapter duplicates each mono frame across the obtained channel layout in a reusable buffer. Frame offsets and return values remain measured in frames, so stereo interleaving cannot change duration or event position.

`FrameAudioStreamOwner` opens the backend before publishing a renderer, allowing the renderer timeline to use the obtained sample rate and the render block to use the obtained burst size. The renderer atomically adopts prepared waveform bindings for new voices while each active voice retains the waveform on which it began, preserving tails through live sound changes. The owner tracks backend start separately from render-loop liveness, advances absolute frame ownership through complete and partial writes, resets retained waveform tails at every start, failure, resync, and stop boundary, and makes stop idempotent. Render failures halt the loop until an explicit resync or stream replacement; resync requires a previously started backend and never arms an unopened track. Rejected operations report to the sink registered for the responsible call.

A renderer publication may bind `TimelineFrameStreamRecovery` to the same immutable timeline. On resync, the owner first synchronizes recovery to frames actually written, then `DeadlineRecovery` counts events expired by the discontinuity without enumerating them. Recovery clamps its event cursor to the first event frame because leading output silence belongs to the stream but precedes the timeline's valid event domain. Only after recovery succeeds does the owner reset waveform tails and move `nextFrame`; backward or mismatched recovery leaves both unchanged.

`StandardPreparedFrameRendererFactory` and `PolyrhythmPreparedFrameRendererFactory` publish the timeline, renderer, initial prepared beat/rhythm references, and recovery owner as one stream-scoped unit after backend open. Timeline construction uses the obtained sample rate, and later live sound adoption publishes already prepared references without copying or resolving resources inside render blocks.

`AudioTrackFrameSession` is the Android render-thread driver for the frame path. It opens `AudioTrackRenderBackend`, publishes one prepared renderer, and repeatedly performs blocking burst-sized frame renders until stop or a typed failure. Its bounded stop posts teardown to the render owner, and a failed render closes the stream instead of spinning.

Each publication carries its session origin as the first output frame, so the session never assumes frame zero. Start is a bounded selection operation that returns success directly to its caller. Renderer role counters commit only for successful unmuted blocks, while the session exposes session-relative beat/rhythm totals, written-frame ownership, obtained properties, block count, and bounded failure history through a sequence-stamped consistent snapshot. When `AudioTimestamp` is available, the same snapshot correlates the absolute written frontier with the platform-presented frame and monotonic presentation time sampled after that write. The render loop reuses one timestamp holder and stores primitive fields, so correlation adds no render-thread allocation.

An increased backend underrun count creates a discontinuity boundary. Consecutive presentation timestamps estimate device-inserted silence as elapsed wall-clock frames minus frames actually presented; recovery advances the logical render frontier by that gap, drops expired events, and resets retained waveform tails before the next block. The snapshot accumulates skipped frames for diagnosis. A failed resync stops the stream instead of continuing with ambiguous ownership. Failures overwrite a fixed single-writer ring without render-thread collection allocation.

`FramePlaybackPublicationBoundary` translates legacy standard and polyrhythm inputs into typed frame-publication factories on the control path. Missing sounds and rejected domain inputs return explicit failure codes, and domain rejections retain their `PlaybackInputFailure` cause for later authoritative failed states. Unsupported subdivisions, additive steps, tempos, accent patterns, and ratios cannot escape toward stream start.

`MetronomeAudioEngine` uses the prepared frame session as its only sound-output path. A rejected publication or stream start abandons focus, refuses to start timing callbacks, and notifies the ViewModel to roll back its optimistic play state; it cannot resurrect the removed pending-click mixer. Handler callbacks drive visual notifications only. Standard tempo, groove, and pattern changes enqueue an in-stream timeline replacement rather than stopping `AudioTrack`. The old timeline chooses its next event boundary; the replacement anchors there with the same absolute event index, preserving frame ownership, musical phase, monotonic event identity, and active waveform tails while the new tempo governs subsequent intervals.

Live mute changes update only the renderer's audible gate because muting must neither rebuild the timeline nor disturb frame continuity and retained waveform tails. Configuration updates use the same render-thread queue, so neither path blocks the metronome callback thread on stream teardown.

Live sound changes prepare off the render path and publish through the frame-session control queue. A successful publication changes the binding for subsequently created voices without rebuilding the stream, timeline, phase, or event identity; ownership and transport adopt the snapshot only after that acknowledgement. A failed, rejected, stale, or superseded publication preserves the last confirmed audible snapshot. Sound changes while stopped prepare only the next session and do not claim current audibility.

Live polyrhythm changes use the same in-stream replacement mechanism. Because changing either ratio invalidates the individual slot grids, the old audio and visual timelines apply the pending configuration at their next coincident cycle boundary; the replacement starts both rhythms there and carries forward event and cycle identity. An active frame polyrhythm is retuned before any new start is considered, so a rejected duplicate start cannot create another output stream.

Visual callbacks use one-shot monotonic delays to the next intended event minus the approved lookahead. A late callback skips every fully expired interval in constant time and emits only the current event, matching audio recovery's no-catch-up policy. Each callback advances from the intended time and schedules exactly one successor, removing the one-millisecond polling wakeup without making Handler dispatch the audio-position authority.

The stream begins writing at the session origin, while the timeline's event origin is shifted by the approved first-beat delay derived from the obtained sample rate. Keeping those origins distinct makes the delay actual rendered silence, preserves session-relative written-frame metrics, and avoids pretending that unowned frames elapsed before `AudioTrack` started.

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
