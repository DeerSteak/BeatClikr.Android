# ADR 0001: Musical Time

**Status:** Accepted
**Date:** 2026-07-28  
**Decision owners:** Product and audio architecture

## Context

The shipping engine derives deadlines from a monotonic clock, but several musical behaviors are implicit in ViewModel and engine code. The replacement scheduler needs a stable product contract before implementation so that tests validate intended behavior instead of preserving implementation accidents.

The sibling iOS app is the reference product behavior. Android mirrors it unless an Android platform constraint prevents parity or an explicit decision records an intentional divergence.

### Approved Android divergence

On 2026-08-03, the product owner designated Android `release/4.1.0` as the authority for configuration-boundary behavior and explicitly confirmed that divergence during PR #43 review. Current iOS restarts the standard timeline when tempo or groove changes; Android intentionally preserves the pending event and phase under MT-019, MT-020, and MT-028. Polyrhythm changes continue to restart both roles together under MT-021. This explicit divergence preserves the established Android release behavior while the refactored production path and permanent qualification suite prevent drift.

## Decision

The clauses below are normative. Scheduler, renderer, playback, and UI acceptance tests cite these identifiers.

### Tempo and meter

- **MT-001:** BPM always means quarter notes per minute.
- **MT-002:** The supported tempo range is 30 through 240 BPM inclusive.
- **MT-003:** Scheduling and persistence accept decimal BPM values without rounding. Direct slider and increment controls select whole BPM values, matching iOS; imported decimal values remain valid and may be displayed with up to two fractional digits.
- **MT-004:** Quarter, eighth, triplet, and sixteenth grooves divide each quarter note into one, two, three, or four scheduler ticks.
- **MT-005:** An odd-quarter pattern uses quarter-note pattern steps. An odd-eighth pattern uses eighth-note pattern steps.
- **MT-006:** The first step of an odd-meter pattern is always accented. Every subsequent step is accented or unaccented according to its value in the selected pattern.

### Accents and feedback

- **MT-007:** Standard playback repeats one sound pattern per quarter note. Tick zero is a beat event and any remaining ticks in that quarter note are subdivision events; the engine does not create a separate measure-level downbeat class.
- **MT-008:** In standard playback, tick zero uses the selected beat sound and the remaining ticks use the selected rhythm sound. In odd-meter playback, each `true` pattern step uses the selected beat sound and each `false` step uses the selected rhythm sound.
- **MT-009:** When alternate sixteenths are enabled, ticks zero and two use the selected beat sound while ticks one and three use the selected rhythm sound. Visual, haptic, and flash feedback still pulse only on tick zero.
- **MT-010:** The global metronome mute suppresses audio without removing musical events, phase, diagnostics, or enabled secondary-output events.

### Start, stop, and count-in

- **MT-011:** A new standard-metronome session starts at tick zero and its first audible event uses the selected beat sound.
- **MT-012:** A new polyrhythm session starts both streams at the same cycle origin.
- **MT-013:** Start has no implicit count-in. A future count-in is a separate explicit mode and is not part of the recorded practice duration.
- **MT-014:** Stop prevents all not-yet-presented events, ends the current phase, and does not preserve a resumable cursor.

### Polyrhythm

- **MT-015:** A displayed ratio `M:N` means the rhythm stream emits `M` evenly spaced events while the reference beat stream emits `N` evenly spaced quarter-note events in one cycle.
- **MT-016:** A polyrhythm cycle lasts `N` quarter notes. The reference interval is `60 / BPM` seconds and the rhythm interval is `N × 60 / (BPM × M)` seconds.
- **MT-017:** `M` and `N` are independently selectable from 1 through 15 inclusive.
- **MT-018:** Coincident stream events share the same intended sample frame and are mixed once at that frame without serial timing displacement.

### Configuration boundaries

- **MT-019:** A user tempo change during standard playback preserves the pending next event and pattern phase; the new tempo governs subsequent intervals without ending the logical playback period.
- **MT-020:** A groove or odd-meter pattern change during playback preserves the pending next event and current event index; the new complete configuration governs that event and subsequent phase without ending the logical playback period.
- **MT-021:** A polyrhythm tempo or ratio change during playback restarts both streams together at a new shared cycle origin without ending the logical playback period.
- **MT-022:** A beat sound, rhythm sound, or sound-bank change prepares the replacement off the render thread and publishes it atomically for subsequently created voices without ending the logical playback period or cutting an active waveform tail. Global mute changes only the renderer's audible gate and does not change musical phase.
- **MT-023:** Playback commands are serialized in submission order. Every accepted configuration publication is complete, and commands coalesced before one render boundary publish only the final valid configuration.

### Tempo ramp

- **MT-024:** Tempo ramp is available only for the instant metronome. It is not applied to song, playlist, or polyrhythm playback.
- **MT-025:** The supported ramp increments are 1, 2, 5, and 10 BPM, and the supported intervals are 4, 8, 16, 32, 48, and 64 beat events.
- **MT-026:** Starting playback resets the ramp counter. The initial beat establishes the counter at zero; each subsequent selected interval of beat events raises tempo by the selected increment, capped at 240 BPM.
- **MT-027:** A beat event for ramp counting is tick zero in a standard groove and every accented step in an odd-meter pattern. Subdivision events and alternate-sixteenth offbeats do not advance the counter.
- **MT-028:** Each ramp increment follows the phase-preserving standard tempo update in MT-019 without ending the logical playback period.
- **MT-029:** Stopping a ramped session restores the tempo captured when that session started.

### Deadline recovery

- **MT-030:** A delayed callback never moves the musical time base and never emits a catch-up burst.
- **MT-031:** Events whose presentation deadlines have passed are counted and dropped. Phase advances directly to the first future event derived from the original session origin.
- **MT-032:** Integer or rational sample-frame arithmetic carries fractional remainder so that quantization cannot accumulate long-run drift.

## Consequences

Phases 2 through 4 replaced the legacy polling/output path with exact frame timelines, arithmetic deadline recovery, approved configuration boundaries, prepared frame rendering, and audio-confirmed authoritative starts. The permanent qualification suite protects those clauses; Phase 8 remains responsible for final physical presentation evidence.

The contract deliberately mirrors `release/4.1.0` behavior: quarter-note BPM, beat/rhythm sound mapping, additive odd meters, `M:N` polyrhythm labeling, phase-preserving standard updates, shared-origin polyrhythm restarts, tempo-ramp counting, a beat-first start, and no hidden count-in.

This decision intentionally does not make one estimator part of the musical-time contract. Phase 7 replaced wall-clock arithmetic and simple averaging with elapsed-realtime nanoseconds, interval rejection, inactivity reset, and a median estimator while preserving the approved range and whole-BPM direct-control behavior.
