# Models

BeatClikr stores structured user data with Room and represents musical choices with Kotlin enums and value objects.

## Room entities

| Model | Purpose |
| --- | --- |
| `Song` | Title, artist, tempo, meter, groove, optional odd-meter pattern, and legacy sequence fields |
| `Playlist` | Named ordered collection with creation time |
| `PlaylistEntry` | Song membership and sequence within a playlist |
| `PracticeSession` | One stable civil calendar day of qualified practice |
| `PracticedSong` | Duration, completed-period count, and a snapshot of the practiced item |
| `PracticeAccountingCheckpoint` | Idempotent lifecycle-accounting and recovery position |

`PlaylistEntry` cascades deletion from its playlist or song. Practice items cascade from their session. UUIDs are persisted through Room converters. Relation projections (`PlaylistWithEntries`, `PlaylistEntryWithSong`, and `PracticeSessionWithSongs`) assemble screen-ready graphs without making them independent entities.

## Practice-day identity

Practice sessions are grouped by the civil day and zone observed at each accounting checkpoint. The entire monotonic interval since the prior checkpoint belongs to that selected bucket; exact midnight and timezone boundaries are not invented. Repeated periods for the same stable item ID accumulate duration and completed-period counts. Metronome and polyrhythm use reserved synthetic IDs whose labels are resolved by the UI rather than persisted as English display text.

Room version 5 is current. Version 4 is the supported preserving migration baseline; unknown versions 1–3 are destructively recreated. Migrations after version 5 must preserve user data and ship with schema fixtures.

## Musical models

- `ExactFraction` and `ExactTempo` retain decimal BPM and frame-period calculations without binary floating-point accumulation.
- `StandardMetronomeConfiguration` combines exact tempo with immutable regular or additive timing, alternate-sixteenth state, and mute state.
- `PolyrhythmConfiguration` uses the iOS-compatible `bpm`, `beats`, and `against` names, where `beats` is the Rhythm count and `against` is the Beat count. Cycle and stream intervals remain exact fractions.
- `AccentPattern` defensively copies additive accents and requires an accented first step.
- `SessionOrigin` binds a nonnegative `sessionID` to an `originFrame`, and `EventSequence` provides a strictly increasing event `index` within that session.
- `FrameEvent` assigns a session sequence and one intended sample frame to one standard voice or two coincident polyrhythm voices. Each voice carries its musical role, abstract sound role, beat identity, and cycle position.
- `AbsoluteAudioTimeline` mirrors the iOS timeline name while replacing floating-point sample positions with independently rounded exact rational frame positions.
- `StandardMetronomeTimeline` generates immutable standard events intersecting an absolute `FrameRange`. Event frames and sequences derive only from the session origin and global musical index, so request boundaries cannot change phase.
- `PolyrhythmTimeline` derives both streams from one exact cycle grid. Coincident voices share one `FrameEvent`, while each voice retains its own cycle position and every emitted event has one monotonic session sequence.
- `TempoRampState` is an immutable instant-metronome reducer. It counts only standard beats and additive accents, preserves the starting tempo, caps exact increments at 240 BPM, and emits the BPM that must restart the standard timeline.
- `PlaybackCommand` defines typed, immutable start, stop, tempo, groove, pattern, sound, bank, mute, polyrhythm, and ramp requests. Every request carries its target phase session, monotonic command sequence, and submission timestamp.
- `CommandBoundary` is the Android-free qualification model for atomic command batches. Shipping control currently executes `PlaybackCoordinator.applyIntent`, so its clauses are not production-qualified until the permanent assertions are ported to that reducer.
- `PlaybackInputBoundary` translates domain invariant rejections into typed `PlaybackInputFailure` values before accepted commands can reach production rendering.
- `DeadlineRecovery` owns the first unprocessed frame for one timeline session and mode. Each timeline counts an expired range arithmetically without enumerating its events; recovery then returns only events inside the current render window, preserves the original origin, and rejects stale-session, moved-origin, or wrong-mode state. `recoverTo` advances only the expired boundary for stream discontinuities, leaving future-window commitment to the renderer and written-frame owner.
- `DeadlineDiagnostics` separates missed event deadlines, dropped events, committed events, and recovery-window counts. Its session and mode identity make aggregation unambiguous without adding Android dependencies to the timing core.
- `Groove` defines quarter, eighth, triplet, sixteenth, and odd-meter modes.
- `BeatPattern` converts additive groupings such as 3+2+2 into accent arrays.
- `PolyrhythmGrid` maps two counts onto their least-common-multiple grid.
- `SoundFile` maps each selectable instrument to acoustic and synthetic Android resources.
- `SoundBank` selects acoustic or synthetic mappings.
- `ClickerType` distinguishes instant and song playback contexts.

The Phase 2 music package is deliberately Android-free. Its exact configurations, timelines, commands, boundary reducer, ramp state, and deadline recovery can therefore be simulated for hours and fuzzed with deterministic command sequences in ordinary JVM tests.

Where concepts already exist on iOS, the Android-free model uses the same terminology: `bpm`, `beats`, `against`, `subdivisions`, `alternateSixteenth`, `muteMetronome`, `sessionID`, `cycleIndex`, and event `index`. New frame-domain concepts retain their contract-oriented names until both platforms adopt a shared replacement model.

User-facing sound-bank and sound-file labels are resource-backed. Stable raw values and contract identifiers remain untranslated; pattern notation such as `3+2+2` is data rather than prose.

The Android-free models intentionally do not contain resource IDs, Android clocks, handlers, audio objects, or persistence annotations. Production rendering consumes their exact frame timelines through the Android integration boundary.
