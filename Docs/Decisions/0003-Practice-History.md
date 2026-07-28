# ADR 0003: Practice History

**Status:** Proposed  
**Date:** 2026-07-28  
**Decision owners:** Product and data architecture

## Context

The current Android app records some practice entries when play is requested rather than when playback is confirmed. It does not persist enough information to apply a consistent duration threshold, repeated-play count, or timezone policy.

The sibling iOS practice model is the reference behavior. Android mirrors its confirmed-playback duration, playback-period count, 30-second qualification, stable local-day identity, and legacy qualification policy where Android storage constraints allow.

## Decision

### Qualification and accounting

- **PH-001:** Practice time begins only when authoritative playback enters `Playing` and the first audio event is committed for presentation.
- **PH-002:** Practice time ends when authoritative playback leaves `Playing`. Preparation, count-in, failure, pause, interruption, and stopped time do not count.
- **PH-003:** Elapsed practice duration uses a monotonic clock. Wall-clock changes cannot add or remove elapsed practice time.
- **PH-004:** An item qualifies for visible daily history after its accumulated confirmed playback reaches 30 seconds on that local day.
- **PH-005:** Confirmed periods shorter than 30 seconds still accumulate toward that day's threshold. A day remains hidden until the threshold is reached.
- **PH-006:** Each transition into confirmed `Playing` begins one playback period. Repeated song, metronome, and polyrhythm plays increment period count and accumulated duration.
- **PH-007:** Focus-denied starts, preparation failures, and sessions stopped before confirmed playback add neither duration nor period count.
- **PH-008:** History stores duration with subsecond internal precision and presents user-facing duration rounded consistently.

### Identity

- **PH-009:** A song is aggregated by stable song ID, not mutable title or artist text.
- **PH-010:** Standard metronome and polyrhythm practice use stable reserved identities and follow the same duration and period-count rules as songs.
- **PH-011:** Editing or renaming an item does not split its historical identity.

### Local day and timezone

- **PH-012:** A daily bucket represents the device-local civil date on which each interval of practice occurred.
- **PH-013:** A session crossing local midnight is split at the boundary so elapsed time is assigned to the correct dates.
- **PH-014:** A timezone or UTC-offset change closes the active accounting segment and opens a new segment in the new local context without stopping audio.
- **PH-015:** Travel and later timezone changes never relabel or merge already stored daily buckets.
- **PH-016:** Daylight-saving transitions use monotonic elapsed duration and local civil-date boundaries. Repeated or skipped wall-clock times neither duplicate nor erase practice duration.

### Data integrity

- **PH-017:** Duration and period-count updates are transactional and survive process death without double counting.
- **PH-018:** Reminder and streak calculations use only qualified daily history.
- **PH-019:** Schema migration preserves existing records. Legacy entries without duration receive 30 seconds so previously earned history and streaks remain qualified, matching the iOS migration policy.

## Consequences

Implementing this contract requires duration, period-count, and accounting state beyond the current schema. Phase 4 supplies authoritative playback events and Phase 6 supplies transactional persistence and migration.

The 30-second threshold matches the existing product intent while preventing taps, denied focus, and immediately stopped playback from creating misleading practice history.
