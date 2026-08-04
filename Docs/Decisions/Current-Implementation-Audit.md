# Current Android Contract Audit

**Audit date:** 2026-07-28  
**Contract state:** Accepted  
**Implementation reviewed:** historical pre-remediation tree at `c175888` (2026-07-28)

> Historical snapshot: this file records the gap analysis that initiated remediation. It is not a description of the current implementation; use the authoritative decisions and `Docs/Validation.md` for current behavior and evidence.

> Contract supersession: this audit compared commit `c175888` with the contract accepted on 2026-07-28. On 2026-08-03, the product owner designated Android `release/4.1.0` as the authority for configuration-boundary behavior. ADR 0001 now records the intentional Android divergence from current iOS: standard changes preserve phase, while polyrhythm changes restart at a shared origin. References below to tick-zero standard replacement describe the superseded contract reviewed by this historical audit.

## Purpose

This audit compares the current Android implementation with the accepted Phase 1 contracts. It is a present-state assessment, not an acceptance result. A behavior can exist without sufficient tests, and a favorable debug-device measurement does not satisfy a release gate that requires longer or different evidence.

Contract conformance and delivery priority are separate. The core release is blocked by musical timing, authoritative foreground playback, focus ownership, navigation, practice integrity, and applicable budgets. Background/locked playback, system controls, and any optional coexistence mode remain planned extended parity rather than core-release blockers.

## Status definitions

| Status | Meaning |
| --- | --- |
| Conforms | The current implementation behaves according to the clause, with direct code or test evidence |
| Partial | Some required behavior exists, but an important case, guarantee, or evidence layer is missing |
| Differs | Android currently behaves differently from the accepted contract |
| Unevidenced | The implementation may support the behavior, but current evidence cannot establish it |

## Decision-driving differences

These differences deserve product review before implementation work begins.

| Contract | Android today | Accepted contract | Assessment |
| --- | --- | --- | --- |
| MT-019, MT-020, MT-028 | Tempo, groove, pattern, and ramp changes mutate the running polling schedule without resetting `nextBeatTimeNanos` or the normal subdivision counter | Restart the standard timeline at tick zero without ending the logical practice period | Differs; adopting the contract intentionally changes audible phase at every such change |
| MT-022 | Sound replacement is asynchronous and does not restart playback; sound-bank changes clear the cache while the timeline continues | Prepare sounds off the render thread and restart at the mode origin | Differs; the contract favors a deterministic boundary over uninterrupted phase |
| MT-030 through MT-032 | Late polling callbacks advance one event per callback and can emit a rapid catch-up sequence; nanosecond interval truncation can accumulate error | Drop expired events, preserve the absolute origin, and carry fractional sample-frame remainder | Differs; this is the core scheduler replacement |
| PL-002 through PL-005 | ViewModels set `isPlaying` and record practice immediately after an asynchronous start request; focus denial is silent and commands have no session identity | Audio-confirmed authoritative state with typed failures and stale-session rejection | Differs; current UI and history can claim playback that never started |
| PL-007 | `MainActivity` and both playback ViewModels stop when the app pauses | Continue active audio in background and under lock | Differs; this is a deliberate product and Android-service change |
| PL-011, PL-012 | No playback foreground service, media session, media notification, or lock-screen transport exists | Provide Android background media infrastructure with stop-capable controls | Missing |
| PL-014 through PL-016 | The engine requests indefinite `AUDIOFOCUS_GAIN`, matching the approved ownership policy, but abandons focus only on engine release | Own long-duration audio focus while playing and abandon it whenever playback ends | Partial; focus acquisition already matches, focus lifetime does not |
| PL-018, PL-021 | No route-change listener or Bluetooth warning exists | Stop on route changes and identify Bluetooth latency variability | Missing |
| PL-028 | Leaving an on-screen metronome usually stops it through Composable disposal, but song playback can continue across some top-level navigation because the navigation helper stops only the opposite metronome mode | Every top-level section change stops all playback | Partial and path-dependent |
| PH-001 through PH-010 | A row is written immediately on a ViewModel start request; there is no confirmed-playback session, duration, threshold, monotonic accounting, or idempotency | Count confirmed playback time and periods, expose a day only after 30 cumulative seconds, and reject failed starts | Differs; the current history is a launch counter rather than practice-duration history |
| PH-014 through PH-019 | Practice rows contain one wall-clock timestamp and are interpreted in the device's current timezone; there is no stable local-day key, timezone/calendar metadata, duration checkpoint, or transactional active-session state | Mirror iOS checkpoint attribution using a stable local-day key, original absolute timestamp, and process-death-safe accounting | Differs |
| TB-008 | The one-hour Pixel 8a workload reported four `AudioTrack` underruns | Zero underruns in normal-use and UI-stress one-hour tests | Current evidence fails the accepted gate |

## Musical-time clause audit

| Clause | Status | Current Android evidence or difference |
| --- | --- | --- |
| MT-001 | Conforms | Standard and polyrhythm intervals use `60 / BPM` with the reference stream treated as quarter notes |
| MT-002 | Conforms | Metronome and polyrhythm ViewModels clamp tempo to 30–240 BPM |
| MT-003 | Partial | Float persistence and scheduling retain decimals and increment buttons move by one BPM, but the slider is continuous and the primary display always formats to zero decimals |
| MT-004 | Conforms | `Groove` maps quarter, eighth, triplet, and sixteenth to 1, 2, 3, and 4 subdivisions |
| MT-005 | Conforms | Odd-quarter and odd-eighth grooves use 1 and 2 subdivisions per quarter |
| MT-006 | Conforms | Every `BeatPattern.accentArray` begins with `true` and derives later accents from additive groups |
| MT-007 | Conforms | Standard mode treats counter zero as the beat and all other subdivision ticks as rhythm events |
| MT-008 | Conforms | The engine maps beat/accent events to the selected beat sound and other events to the rhythm sound |
| MT-009 | Conforms | Alternate sixteenths use the beat sound on ticks zero and two while only tick zero reports `isBeat` |
| MT-010 | Conforms | Mute suppresses waveform enqueueing while delegates and timeline counters continue |
| MT-011 | Conforms | Standard start resets the subdivision counter to zero and schedules that beat first |
| MT-012 | Conforms | Polyrhythm start resets `stepIndex` to zero, where both streams fire |
| MT-013 | Conforms | No count-in exists |
| MT-014 | Partial | Stop clears callbacks, pending clicks, active clicks, and counters, but asynchronous commands have no session identity to prove that stale queued work cannot survive every race |
| MT-015 | Conforms | The displayed first value drives the rhythm stream and the second value drives the reference beat stream |
| MT-016 | Conforms | `PolyrhythmGrid` and `PolyrhythmTimingEngine` implement a cycle lasting the second ratio value in quarter notes |
| MT-017 | Conforms | Both ratio values are clamped to 1–15 |
| MT-018 | Conforms | Coincident polyrhythm sounds are enqueued together and begin at the same render-buffer offset |
| MT-019 | Differs | `updateTempo` changes fields in place and does not restart at tick zero |
| MT-020 | Differs | Groove and pattern changes use the same in-place update |
| MT-021 | Conforms | Polyrhythm BPM or ratio changes call `start`, resetting both streams to a shared origin |
| MT-022 | Differs | Sound and bank changes do not prepare-and-restart at a defined musical boundary |
| MT-023 | Differs | There is no sequenced immutable command model or atomic boundary application |
| MT-024 | Conforms | Ramp is handled only by the instant metronome callback path |
| MT-025 | Conforms | The UI exposes increments 1, 2, 5, and 10 and intervals 4, 8, 16, 32, 48, and 64 |
| MT-026 | Conforms | `RampController` resets to −1, skips the initial beat, advances at the interval, and caps at 240 BPM |
| MT-027 | Conforms | Ramp receives only `isBeat`; odd-meter accents are reported as beats and subdivisions are not |
| MT-028 | Differs | Ramp invokes the in-place `updateTempo` path rather than restarting at tick zero |
| MT-029 | Conforms | Stopping an instant ramp session restores the BPM captured at start |
| MT-030 | Differs | A late callback can cause subsequent one-millisecond polls to emit overdue events in a catch-up sequence |
| MT-031 | Differs | Expired events are not counted and dropped |
| MT-032 | Differs | Each floating interval is truncated to a whole nanosecond without a carried remainder, and rendering is not sample-frame scheduled |

## Playback-lifecycle and output clause audit

| Clause | Status | Current Android evidence or difference |
| --- | --- | --- |
| PL-001 | Partial | `AudioPlayerService` is application-scoped, but each ViewModel owns an independent optimistic playback flag and lifecycle policy |
| PL-002 | Differs | Start returns before the handler requests focus, starts `AudioTrack`, or commits the first event |
| PL-003 | Differs | UI and practice history treat the ViewModel request as successful playback |
| PL-004 | Differs | Focus denial returns silently from the engine while the ViewModel remains playing |
| PL-005 | Differs | Commands and callbacks carry no session identity |
| PL-006 | Conforms | `AudioPlayerService` stops the other mode before starting standard or polyrhythm playback |
| PL-007 | Differs | `MainActivity.onPause` and process lifecycle observers stop playback |
| PL-008 | Partial | Focus loss stops both modes, but route loss and media-server recovery are not modeled |
| PL-009 | Conforms | No automatic-resume path exists |
| PL-010 | Conforms | Explicit start resets counters and establishes a new first-event origin |
| PL-011 | Differs | No playback foreground service is declared or implemented |
| PL-012 | Differs | No media session, playback notification, or lock-screen controls exist |
| PL-013 | Partial | The preference controls `FLAG_KEEP_SCREEN_ON` only while the Activity is visible, but it remains set while stopped |
| PL-014 | Conforms | The engine requests long-duration `AUDIOFOCUS_GAIN` before playback |
| PL-015 | Conforms | Current ownership allows Android to pause or duck other media and does not guarantee backing-track coexistence |
| PL-016 | Differs | Focus is abandoned only during engine release, not whenever playback stops |
| PL-017 | Partial | Generic `AudioTrack` output can use speaker, wired, and USB routes, but route readiness and low-latency eligibility are not inspected |
| PL-018 | Differs | No audio-device callback stops and rebuilds playback on a route change |
| PL-019 | Unevidenced | Bluetooth is not explicitly prohibited, but no Bluetooth route behavior is identified or tested |
| PL-020 | Unevidenced | Existing software timing evidence is not Bluetooth-specific |
| PL-021 | Differs | The UI has no Bluetooth latency warning |
| PL-022 | Partial | Audio callbacks are the source of secondary events, but waveform enqueueing is not proof of presentation |
| PL-023 | Partial | Audio and secondary effects share one callback and predicted time, but they do not derive from an authoritative committed sample-frame event |
| PL-024 | Partial | Visual timing uses a buffer-derived output-latency estimate; per-output measured confidence and route calibration are absent |
| PL-025 | Partial | Effects stop when the app pauses, but only because all playback stops; foreground-only effects alongside continuing background audio are not implemented |
| PL-026 | Conforms | Disabling vibration or flashlight does not modify the audio schedule |
| PL-027 | Partial | Output services avoid crashing playback, but failures are not surfaced to the user |
| PL-028 | Partial | Top-level behavior depends on which screen owns playback; not every transition performs an explicit global stop |
| PL-029 | Conforms | Compact-mode switching stops the interface being hidden |
| PL-030 | Conforms | Library and Playlist sheets, editors, detail navigation, pickers, and focus mode do not intentionally stop active song playback |
| PL-031 | Partial | Explicitly playing another Library or Playlist song restarts standard playback for that song, but authoritative duration transfer awaits the practice implementation |

## Practice-history clause audit

| Clause | Status | Current Android evidence or difference |
| --- | --- | --- |
| PH-001 | Differs | History is recorded immediately after a start request rather than confirmed audio |
| PH-002 | Differs | No playing interval or elapsed duration is tracked |
| PH-003 | Differs | History has no monotonic elapsed-time accounting |
| PH-004 | Differs | Entries are visible immediately; no 30-second threshold exists |
| PH-005 | Differs | Short periods neither accumulate duration nor remain hidden |
| PH-006 | Partial | Repeated songs increment `timesPracticed`, but metronome and polyrhythm rows are capped at one per day and no duration accumulates |
| PH-007 | Differs | A focus-denied asynchronous start can still create history because the ViewModel records immediately |
| PH-008 | Differs | The schema stores no duration |
| PH-009 | Differs | There is no active session identity or idempotent start-notification handling |
| PH-010 | Differs | Switching items does not checkpoint elapsed time because no active accounting period exists |
| PH-011 | Conforms | Songs aggregate by stable UUID string |
| PH-012 | Conforms | Metronome and polyrhythm use stable reserved IDs |
| PH-013 | Conforms | Renaming a song preserves its ID and does not split aggregation |
| PH-014 | Partial | Rows are placed in the current local day when created, but a stable civil-date key, timezone/calendar metadata, and explicit original-instant semantics are not stored |
| PH-015 | Differs | No duration checkpoint chooses the current local-day record or attributes a monotonic interval |
| PH-016 | Differs | Timezone changes cannot affect checkpoint bucket selection because no active accounting exists |
| PH-017 | Differs | Stored timestamps are converted using the current timezone, so travel can relabel the displayed civil day |
| PH-018 | Differs | There is no duration model to protect against repeated or skipped DST time |
| PH-019 | Differs | Session creation and practiced-item insertion or update are separate DAO operations, with no active checkpoint or double-count protection |
| PH-020 | Differs | Every stored row qualifies for streaks immediately |
| PH-021 | Differs | Version 4 has no duration or period-count migration; pre-release versions 1–3 are intentionally recreated |

## Quantitative budget audit

| Budget | Current status | Evidence |
| --- | --- | --- |
| TB-001 | Differs architecturally | The 30-minute callback run reported zero scheduled drift, but there is no rational sample-frame scheduler or 12-hour multi-rate simulation |
| TB-002 | Partial | Polyrhythm coincidence is implemented, but no intended-frame property test covers all modes and the renderer queues two waveforms rather than one frame-domain event |
| TB-003 | Differs | The polling engine can emit catch-up events after a stall |
| TB-004 | Partial | The two-minute 240 BPM recording had no missing or doubled onsets; the required one-hour dense run is absent |
| TB-005 | Partial | The same recording met the percentile limits for one short maximum-density condition, not the required one-hour multi-condition population |
| TB-006 | Partial | The two-minute fitted endpoint error was −2.536 ms; the required one-hour drift evidence is absent |
| TB-007 | Fails in current reference | The corrected 30-cold/30-warm release-equivalent Pixel 8a run fails p50 for both populations and p95/p99 for cold starts; the earlier passing calculation mixed clock domains and is invalid |
| TB-008 | Conforms in current reference | The release-equivalent five-minute, 30-minute, and one-hour maximum-density runs all reported zero underruns |
| TB-009 | Differs | Tempo changes neither restart at the contract origin nor operate in sample frames |
| TB-010 | Unevidenced | No atomic command-boundary implementation or randomized test exists |
| TB-011 | Unevidenced | Visuals use predicted timing, but no synchronized high-speed measurement exists |
| TB-012 | Unevidenced | No external haptic-onset measurement exists |
| TB-013 | Unevidenced | No photodiode or high-speed flash measurement exists |
| TB-014 | Partial | The release-equivalent 25-minute aggregate profile averaged 14.67% of one core, meeting the mean limit; one-hour and p95 evidence is absent |
| TB-015 | Partial | The release-equivalent 30-minute run decreased from 16.96 MiB to 12.15 MiB PSS, but the required warm-up-to-final one-hour series is incomplete |
| TB-016 | Conforms in current reference | The one-hour release-equivalent battery workload completed with Android thermal status 0 |
| TB-017 | Partial | One release-equivalent unplugged run consumed 2.87 displayed percentage points per hour; the contract requires three documented runs |
| TB-018 | Baseline pinned | The pre-Phase-3 release-equivalent comparator is recorded; the first matched before/after comparison awaits a timing-sensitive change |

## Current strengths worth preserving

The replacement work should preserve Android's already-correct quarter-note tempo model, groove and odd-meter mappings, beat/rhythm sample selection, alternate-sixteenth behavior, mute event continuity, first-event semantics, polyrhythm grid and shared origin, 1–15 ratio range, instant tempo-ramp behavior, stable song and built-in history identities, mutually exclusive audio modes, and secondary-output independence.

The Pixel 8a results also show that the current implementation can sound steady and operate within promising startup, CPU, battery, and short acoustic-error ranges. Those results are valuable baselines even where the implementation does not yet provide the stronger guarantees required by the accepted contract.

## Resolved product decisions

- Configuration changes restart at tick zero, matching iOS.
- Android prioritizes conventional long-duration audio-focus ownership; backing-track coexistence is secondary.
- Top-level section changes stop all playback. Internal Library and Playlist navigation preserves playback, while explicitly playing another song replaces the active song.
- Practice persistence mirrors iOS local-day checkpoint selection, stored day identity and metadata, and original absolute timestamp; it does not perform exact boundary splitting.
- Fixed release budgets remain hard gates, and matched-baseline regression checks prevent a change from making existing performance materially worse within those ceilings.
