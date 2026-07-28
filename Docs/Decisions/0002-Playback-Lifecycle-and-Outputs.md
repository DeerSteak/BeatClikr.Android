# ADR 0002: Playback Lifecycle and Outputs

**Status:** Proposed  
**Date:** 2026-07-28  
**Decision owners:** Product and audio architecture

## Context

Android audio focus, process lifecycle, route changes, display refresh, vibration, and torch control have different timing and failure behavior. Treating them as one clock creates false UI state and unsupported synchronization claims.

The sibling iOS app is the reference product behavior. Android mirrors its background audio, explicit-restart interruption policy, stop-capable system controls, and foreground-only secondary effects using Android-appropriate platform components.

## Decision

### Authoritative playback state

- **PL-001:** Playback has one application-scoped owner and one authoritative state machine.
- **PL-002:** A start succeeds only after required audio resources are prepared, audio focus is granted, an output stream is started, and the first event is committed for presentation.
- **PL-003:** UI, practice history, secondary outputs, and diagnostics observe authoritative playback state. A ViewModel request alone never means that playback started.
- **PL-004:** Start failure reports a typed reason and returns to a non-playing state. It never fails silently.
- **PL-005:** Every callback and command carries a session identity so stale work from an earlier session cannot mutate the current session.

### Background and interruption policy

- **PL-006:** Audio that is already playing continues when BeatClikr is backgrounded or the device is locked. Backgrounding never starts playback.
- **PL-007:** Transient or permanent audio-focus loss stops playback.
- **PL-008:** Playback never resumes automatically after focus loss, media-server loss, output-route change, engine failure, or a user stop from app or system controls.
- **PL-009:** Explicit user play after a stop or interruption creates a new session and a new musical phase origin.
- **PL-010:** Android background playback uses a media-playback foreground service, persistent media notification, media session, and Android-version-appropriate permissions and lifecycle handling.
- **PL-011:** Notification and lock-screen controls provide pause or stop for active playback. They never start or automatically resume a metronome session.

### Route policy

- **PL-012:** Built-in speaker, analog or USB-C wired output, and USB audio interfaces are local routes eligible for the low-latency acceptance budgets when Android reports a stable output stream.
- **PL-013:** A route change stops the current session, records the reason, releases the old stream, and requires explicit user restart after the new route is ready.
- **PL-014:** Bluetooth playback is supported as a convenience route, but BeatClikr does not promise deterministic end-to-end Bluetooth latency.
- **PL-015:** Bluetooth tests still require stable musical phase, no application-generated missing or doubled events, and no catch-up bursts.
- **PL-016:** The UI identifies Bluetooth as latency-variable when it is the active route. It does not imply that speaker measurements apply to Bluetooth.

### Audio and secondary-output alignment

- **PL-017:** Audio presentation is the authoritative musical event.
- **PL-018:** Visual, haptic, and flash outputs derive from the same committed event and intended presentation frame as audio. They do not run independent beat clocks.
- **PL-019:** Each output applies a measured or explicitly estimated lead time in its own clock domain. Unknown device latency is reported as unknown rather than treated as zero.
- **PL-020:** Haptic and flash effects are foreground-only and stop when the visible app becomes inactive even while audio continues.
- **PL-021:** Disabling, denying, or stopping a secondary output does not alter audio phase.
- **PL-022:** Secondary-output failure is surfaced without stopping otherwise healthy audio unless continuing would violate safety or platform policy.

## Consequences

The shipping Android app currently stops when it leaves the foreground and therefore does not satisfy the iOS-parity background contract. It also lacks one authoritative coordinator, and some ViewModels report playing or record practice before focus and audible output are confirmed. Phases 4 and 7 close those gaps.

The successful screen-off engine instrumentation run is useful feasibility evidence, but Android background playback is not complete until the foreground service, media session, notification controls, focus behavior, and lifecycle tests satisfy this decision.
