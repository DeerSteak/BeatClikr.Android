# Phase 1 Decision Review Guide

**Review state:** Approved by the product owner  
**Date:** 2026-07-28

## What approval means

The product owner approved the three ADRs and timing budgets as the implementation and acceptance-test contract. Approval does not assert that the current Android implementation already complies.

Review these documents in order:

1. [Current-Implementation-Audit.md](Current-Implementation-Audit.md)
2. [0001-Musical-Time.md](0001-Musical-Time.md)
3. [0002-Playback-Lifecycle-and-Outputs.md](0002-Playback-Lifecycle-and-Outputs.md)
4. [0003-Practice-History.md](0003-Practice-History.md)
5. [../Timing-Budgets.md](../Timing-Budgets.md)

## Internal review findings

The review compared the contracts with the sibling iOS implementation and documentation, the current Android implementation, and the Pixel 8a evidence captured in Phase 0.

### Decisions that mirror iOS

- Quarter-note BPM, whole-BPM direct controls, groove subdivisions, odd-meter accent patterns, beat/rhythm sound selection, alternate-sixteenth feedback, and `M:N` polyrhythm meaning.
- Beat-first starts, no hidden count-in, phase-preserving standard updates, shared-origin polyrhythm restarts, tempo-ramp choices and counting, and restoration of the starting tempo when a ramped session stops.
- One active playback mode, playback continuing under lock or background, explicit restart after interruption, top-level navigation stopping playback, and internal Library or Playlist navigation preserving playback.
- Confirmed-playback accounting, cumulative 30-second daily qualification, playback-period counts, stable item identities, legacy qualification, and stored local-day identity.

### Intentional Android adaptations

- Background playback uses a foreground media service, notification, and media session.
- Android uses conventional long-duration audio-focus ownership because metronome output is the primary experience. This intentionally differs from the iOS mix-with-others session policy; backing-track coexistence is not guaranteed.
- Bluetooth remains supported without an end-to-end latency promise, while application-generated phase errors, misses, doubles, and catch-up bursts remain defects.

### Intentional product refinements

- Missed deadlines are dropped against an absolute frame timeline instead of producing catch-up clicks.
- Practice checkpoints use the current local civil-day record and original absolute timestamp exactly as iOS does; intervals are not split at exact midnight or timezone boundaries.
- The tap-tempo estimator is not frozen into the ADR; Phase 7 now uses monotonic time, interval rejection, inactivity reset, and median filtering.

### Product-owner clarifications

- Standard configuration changes preserve the pending event and pattern phase; polyrhythm ratio or tempo changes restart both roles together, matching `release/4.1.0` behavior.
- Android prioritizes long-duration audio-focus ownership over backing-track coexistence.
- Top-level section changes stop all playback; internal Library and Playlist navigation continues playback; explicitly choosing another song replaces the active song.
- Practice history uses iOS checkpoint attribution with a stable local-day key and original absolute timestamp, without exact boundary splitting.
- Fixed budgets must be paired with matched-baseline regression checks so unused margin cannot justify making performance worse.
- Core release priority is musical correctness, timing, authoritative foreground playback, Android focus ownership, navigation, and practice integrity. Background/locked playback, system controls, and optional backing-track coexistence remain high-quality extended parity work rather than core-release blockers.

### Quantitative correction

The original start-latency proposal was internally impossible because it allowed less time than the designed first-event pre-roll plus device output latency. On 2026-08-03, the product owner approved TB-007 at p50 ≤ 250 ms, p95 ≤ 300 ms, and maximum ≤ 500 ms. Earlier apparent passing distributions mixed frame and clock domains and are invalid. The corrected 2026-08-03 release-equivalent Pixel 8a run passes the amended gate and is the current reference.

## Approval checklist

- [x] Musical meanings, accents, ramp behavior, restart boundaries, and deadline recovery are correct.
- [x] Background, interruption, navigation, route, system-control, and audio-focus behavior are correct.
- [x] Practice qualification, repeated plays, identity, midnight, timezone, and migration behavior are correct.
- [x] Timing, alignment, CPU, memory, thermal, and battery gates express the desired release quality.
- [x] Product-owner clarifications are incorporated into the accepted clauses.
- [x] Mark each ADR and the timing budgets `Accepted`.
