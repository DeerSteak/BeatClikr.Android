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
- Focus and route readiness use one production model: session-tagged start results reject unavailable focus or output, and session-tagged interruptions stop an active session. There is no parallel prerequisite state channel.
- `AudioOutputRoute.UNKNOWN` means that no usable routed device is available. A usable device whose Android type is not classified is `OTHER`, and may enter `Playing` without inheriting a named route's latency claim.
- A known active route becoming `UNKNOWN` is `RouteUnavailable`; a transition between two usable routes is `RouteChanged(previous, current)`.
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
- Secondary-output visibility follows `ProcessLifecycleOwner` at the `STARTED` boundary. Effects are disabled when no Activity is started, including ordinary backgrounding and screen-off; configuration changes do not produce a process stop.
- Multi-window, the notification shade, and transient system or permission overlays retain effects while BeatClikr remains started. A fully obscuring transition that stops the last Activity disables effects; audio lifecycle remains a separate policy.
- Torch scheduling uses a pulse-off path plus a failsafe. Registration failure triggers an immediate off attempt, then at most one scheduled retry and one terminal immediate attempt; failures remain visible without changing playback.

### Event delivery

- Lifecycle transitions use a lossless in-process journal and current checkpoint, separate from rendered events. The single durable Phase 5 consumer acknowledges persisted sequences so the coordinator can prune them; reads behind that acknowledgement fail explicitly. A 4,096-transition safety cap prevents unbounded growth before a consumer exists and reports an explicit gap if exceeded.
- Rendered events use a bounded recent-history stream. Visual and secondary-output consumers detect sequence gaps, reset transient output, and skip the first post-gap event rather than producing catch-up bursts.
- Diagnostics may inspect bounded replay as best-effort history. Renderer-ring loss remains `RecordsDropped`; downstream stream loss is reported independently by each consumer's delivery cursor.

### Navigation

- **PL-028:** Changing the top-level application section stops all playback.
- **PL-029:** Switching between standard-metronome and polyrhythm interfaces stops playback owned by the interface being hidden.
- **PL-030:** Navigation within Library or Playlist, including detail pushes, editors, pickers, sheets, and focus mode, does not stop active playback.
- **PL-031:** Explicitly playing another song from Library, Playlist, or focus mode replaces the active song at a new tick-zero origin and transfers practice accounting to the new song.

## Consequences

Phase 4 established one authoritative coordinator, audio-confirmed state, session-tagged commands and callbacks, focus ownership, and route policy. Phase 9 adds foreground-service lifetime and stop-only system controls around that same coordinator. Physical background, lock-screen, notification, route-loss, and long-run qualification remains required before closing the phase.

The foreground service and long-duration focus policy are Android platform adaptations. Unlike iOS, Android prioritizes metronome focus ownership over guaranteed mixing with backing tracks. The single-active-mode rule, navigation stops, internal-navigation continuity, song replacement, keep-awake scope, and explicit-restart behavior mirror the iOS application contract.

Phase 9 retains `AUDIOFOCUS_GAIN` as the only approved policy. No separate backing-track coexistence mode has validated demand, so no reduced-focus UI or behavior is implemented. Any future coexistence option requires an explicit amendment to this decision and representative media-player qualification without weakening the default.
