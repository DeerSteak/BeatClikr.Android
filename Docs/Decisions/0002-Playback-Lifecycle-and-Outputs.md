# ADR 0002: Playback Lifecycle and Outputs

**Status:** Accepted
**Date:** 2026-07-28  
**Decision owners:** Product and audio architecture

## Context

Android audio focus, process lifecycle, route changes, display refresh, vibration, and torch control have different timing and failure behavior. Treating them as one clock creates false UI state and unsupported synchronization claims.

The sibling iOS app is the reference product behavior. Android mirrors its background audio, explicit-restart interruption policy, stop-capable system controls, and foreground-only secondary effects using Android-appropriate platform components.

The clauses define the intended completed behavior, not equal delivery priority. Authoritative foreground metronome playback and conventional Android audio-focus ownership are core release requirements. Background/locked playback, system controls, and any optional backing-track coexistence mode are extended parity work: they should be implemented to the same quality standard but do not block the core metronome release.

## Decision

### Authoritative playback state

- **PL-001:** Playback has one application-scoped owner and one authoritative state machine.
- **PL-002:** A start succeeds only after required audio resources are prepared, long-duration audio focus is granted, an output stream is started, and the first event is committed for presentation.
- **PL-003:** UI, practice history, secondary outputs, and diagnostics observe authoritative playback state. A ViewModel request alone never means that playback started.
- **PL-004:** Start failure reports a typed reason and returns to a non-playing state. It never fails silently.
- **PL-005:** Every callback and command carries a session identity so stale work from an earlier session cannot mutate the current session.
- **PL-006:** Standard metronome and polyrhythm playback are mutually exclusive. Starting one mode stops the other before the new session is committed.

### Background and interruption policy

- **PL-007:** Audio that is already playing continues when BeatClikr is backgrounded or the device is locked. Backgrounding never starts playback.
- **PL-008:** Permanent, transient, or duck-capable audio-focus loss, media-server loss, or loss of usable output stops playback.
- **PL-009:** Playback never resumes automatically after an interruption, media-server loss, output-route change, engine failure, or a user stop from app or system controls.
- **PL-010:** Explicit user play after a stop or interruption creates a new session and a new musical phase origin.
- **PL-011:** Android background playback uses a media-playback foreground service, persistent media notification, media session, and Android-version-appropriate permissions and lifecycle handling.
- **PL-012:** Notification and lock-screen controls provide pause or stop for active playback. They never start or automatically resume a metronome session.
- **PL-013:** The keep-screen-awake preference applies only while BeatClikr is visible and playing. It does not control whether background audio continues.

### Audio focus

- **PL-014:** BeatClikr requests conventional long-duration `AUDIOFOCUS_GAIN` before starting playback because metronome output is the primary audio experience.
- **PL-015:** Backing-track coexistence is secondary and is not guaranteed. Android and the other application determine whether existing media pauses or ducks when BeatClikr gains focus.
- **PL-016:** BeatClikr abandons audio focus whenever no mode is playing.

### Route policy

- **PL-017:** Built-in speaker, analog or USB-C wired output, and USB audio interfaces are local routes eligible for the low-latency acceptance budgets when Android reports a stable output stream.
- **PL-018:** A route change stops the current session, records the reason, releases the old stream, and requires explicit user restart after the new route is ready.
- **PL-019:** Bluetooth playback is supported as a convenience route, but BeatClikr does not promise deterministic end-to-end Bluetooth latency.
- **PL-020:** Bluetooth tests still require stable musical phase, no application-generated missing or doubled events, and no catch-up bursts.
- **PL-021:** The UI identifies Bluetooth as latency-variable when it is the active route. It does not imply that speaker measurements apply to Bluetooth.

### Audio and secondary-output alignment

- **PL-022:** Audio presentation is the authoritative musical event.
- **PL-023:** Visual, haptic, and flash outputs derive from the same committed event and intended presentation frame as audio. They do not run independent beat clocks.
- **PL-024:** Each output applies a measured or explicitly estimated lead time in its own clock domain. Unknown device latency is reported as unknown rather than treated as zero.
- **PL-025:** Haptic and flash effects are foreground-only and stop when the visible app becomes inactive even while audio continues.
- **PL-026:** Disabling, denying, or stopping a secondary output does not alter audio phase.
- **PL-027:** Secondary-output failure is surfaced without stopping otherwise healthy audio unless continuing would violate safety or platform policy.

### Navigation

- **PL-028:** Changing the top-level application section stops all playback.
- **PL-029:** Switching between standard-metronome and polyrhythm interfaces stops playback owned by the interface being hidden.
- **PL-030:** Navigation within Library or Playlist, including detail pushes, editors, pickers, sheets, and focus mode, does not stop active playback.
- **PL-031:** Explicitly playing another song from Library, Playlist, or focus mode replaces the active song at a new tick-zero origin and transfers practice accounting to the new song.

## Consequences

The shipping Android app currently stops when it leaves the foreground and therefore does not satisfy the iOS-parity background contract. It also lacks one authoritative coordinator, and some ViewModels report playing or record practice before audio focus and audible output are confirmed. Phase 4 closes those playback and lifecycle gaps.

The successful screen-off engine instrumentation run is useful feasibility evidence, but Android background playback is not complete until the foreground service, media session, notification controls, focus behavior, navigation behavior, and lifecycle tests satisfy this decision.

The foreground service and long-duration focus policy are Android platform adaptations. Unlike iOS, Android prioritizes metronome focus ownership over guaranteed mixing with backing tracks. The single-active-mode rule, navigation stops, internal-navigation continuity, song replacement, keep-awake scope, and explicit-restart behavior mirror the iOS application contract.
