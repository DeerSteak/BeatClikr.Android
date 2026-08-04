# Phase 8 UI-interaction qualification

**Date:** 2026-08-03  
**Device:** Pixel 8a, Android 17, build `CP2A.260705.006`  
**Build:** Minified benchmark, release-equivalent  
**Route:** Built-in speaker, 48 kHz mono, low-latency `AudioTrack`  
**Screen:** On and awake

The external accessibility driver repeatedly changed 240→239→240 BPM and sixteenth→eighth→sixteenth subdivisions on five-second cycles. Every commit retained the same authoritative session and complete configuration. The Activity was recreated every five minutes while playback continued.

| Metric | Result |
| --- | ---: |
| Duration | 60 minutes |
| UI cycles | 720 |
| Playback sessions | 1 |
| Deadline misses | 0 |
| Dropped events | 0 |
| Platform underruns | 0 |
| Recovery-skipped frames | 0 |
| Rendered chunks | 720,356 |
| Intended frames | 172,885,440 |
| Rendered frames | 172,885,440 |
| Written frames | 172,885,440 |

The workload passes TB-008 through TB-010 at the application/render layer. It does not establish acoustic or audio/visual alignment.

Raw artifacts: `benchmarks/raw/phase8/20260804T021803Z-ui-interaction-60m`
