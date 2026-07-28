# ADR 0003: Practice History

**Status:** Accepted
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
- **PH-009:** Repeating a start notification for the same active item and session is idempotent. It does not open another period or reset elapsed accounting.
- **PH-010:** Confirmed playback of a different item closes and checkpoints the active item's period before opening the new item's period.

### Identity

- **PH-011:** A song is aggregated by stable song ID, not mutable title or artist text.
- **PH-012:** Standard metronome and polyrhythm practice use stable reserved identities and follow the same duration and period-count rules as songs.
- **PH-013:** Editing or renaming an item does not split its historical identity.

### Local day and timezone

- **PH-014:** A daily bucket has a stable Gregorian local civil-day key captured in the device timezone, plus the timezone identifier, calendar identifier, and original absolute timestamp used to create the record.
- **PH-015:** Each duration checkpoint selects the bucket for the device-local civil date at checkpoint time and assigns the entire monotonic interval since the previous checkpoint to that bucket. A session crossing midnight is not split at the exact boundary.
- **PH-016:** A timezone or UTC-offset change affects the bucket selected by the next checkpoint without stopping audio. The entire interval since the previous checkpoint is assigned using the local context at that checkpoint.
- **PH-017:** Travel and later timezone changes never relabel or merge already stored daily buckets.
- **PH-018:** Daylight-saving transitions use monotonic elapsed duration, and checkpoint bucket selection uses the current Gregorian local date. Repeated or skipped wall-clock times neither duplicate nor erase practice duration.

### Data integrity

- **PH-019:** Duration and period-count updates are transactional and survive process death without double counting.
- **PH-020:** Reminder and streak calculations use only qualified daily history.
- **PH-021:** Schema migration preserves existing records. Legacy entries without duration receive 30 seconds so previously earned history and streaks remain qualified, matching the iOS migration policy.

## Consequences

Implementing this contract requires duration, period-count, and accounting state beyond the current schema. Phase 4 supplies authoritative playback events and Phase 5 supplies transactional accounting and migration.

The 30-second threshold matches the existing product intent while preventing taps, denied focus, and immediately stopped playback from creating misleading practice history.

The checkpoint attribution, stable local-day key, stored timezone/calendar metadata, and original absolute timestamp mirror the current iOS persistence behavior. This deliberately does not invent exact midnight or timezone-boundary splitting that iOS does not perform.
