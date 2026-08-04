# Phase 8 Pixel 8a Dense Standard Render — 60 Minutes

- Date: 2026-08-03
- Source: `4cf4ac28768e8e88495034a81f4eb03f8308fde8` plus the recorded Phase 8 working tree
- Device: Pixel 8a (`akita`), one USB ADB transport with mDNS auto-connect disabled
- OS: Android 17, `CP2A.260705.006` / `15641320`
- Build: minified `benchmark`, debug-signed, production sounds
- App APK SHA-256: `f6d833e92ff7e95fe430d19c7b97e5faa4a10d56a4ccfaf34f67e08652c828f2`
- Test APK SHA-256: `68fdce6347cce63f83250058404166a2def12ae138678f8bd30a6febedd43771`
- Route: built-in speaker through `AudioTrack`
- Workload: 240 BPM, sixteenth subdivisions, 60 minutes, 57,600 events
- Screen: awake for the full run, automatic brightness value 2, ten-minute timeout with plugged-in stay-awake
- Power: AC powered, battery 100%, battery temperature 26.3→26.4 °C

## Result

The Gradle/UTP test passed in 1 hour 15 seconds. The audio workload ran from 22:10:53Z through 23:10:53Z.

| Metric | Result |
| --- | ---: |
| Scheduled drift | 0.0 ms |
| Deadline misses | 0 |
| Dropped events | 0 |
| `AudioTrack` underruns | 0 |
| Route changes | 0 |
| Rendered chunks | 720,015 |
| Intended frames | 172,803,600 |
| Rendered frames | 172,803,600 |
| Written frames | 172,803,600 |
| Final estimated presented frame | 172,797,600 |
| Mix p50/p95/p99 | 0.020/0.085/0.130 ms |
| Maximum mix duration | 8.944 ms |

The obtained stream was 48 kHz mono with a 240-frame burst, 2,886-frame buffer, and low-latency performance mode. Android thermal status was 0 before and after the run. Callback-arrival statistics and blocking-write duration are retained in the raw log as diagnostics and are not acoustic or mixer-CPU claims.

This passes the maximum-density standard render portion of TB-008. It does not close TB-008's UI-interaction stress requirement, the representative-polyrhythm render requirement, acoustic timing, sustained process CPU/memory, or unplugged battery qualification.

## Artifacts

- `benchmarks/raw/phase8/20260803T221035Z-dense-built-in-60m-qualified/`
- `benchmarks/raw/phase8/20260803T221026Z-dense-built-in-60m-qualified.log`
