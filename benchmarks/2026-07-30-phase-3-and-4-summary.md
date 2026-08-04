# Phase 3 and Phase 4 Device Findings

These 2026-07-30 through 2026-08-02 measurements supported the renderer and playback-lifecycle architecture decisions. Phase 8 later supplied the release qualification.

## Findings

- Short Pixel 8a smokes confirmed production PCM reached `AudioTrack`, exposed obtained stream facts, and exercised the real render path. Wireless ADB duplicates were identified as an operational hazard; physical runs now require exactly one active transport.
- The five-minute release-equivalent maximum-density decision run supported retaining the custom frame renderer on `AudioTrack`. Mix duration upper bounds were 0.040 ms p50, 0.065 ms p95, and 0.225 ms p99, with a 4.223 ms exact maximum. Mix p99 used at most 4.5% of the obtained burst period.
- Blocking-write duration was retained as device-drain pacing rather than mixer CPU cost. AAudio or Oboe comparison remains conditional on a reproducible approved-gate failure or unmet required device coverage.
- Physical built-in-speaker smokes supported audio-focus acquisition and release, authoritative start/stop ownership, and route observation. Later Phase 8 lifecycle evidence superseded the incomplete early route matrix.
- Instrumentation and pure tests became the durable gates for frame ownership, atomic configuration, stale-session rejection, route loss, focus interruption, timestamp correlation, diagnostics, and recovery policy.

Detailed logs and test-result trees were removed from the working tree after their findings were consolidated. Previously committed artifacts remain in Git history.
