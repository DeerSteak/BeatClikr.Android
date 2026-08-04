# Phase 8 polyrhythm smoke tests

Date: 2026-08-03

These benchmark-build instrumentation tests compare rendered event frames and voice metadata with the independent `PolyrhythmContractFixture`. They also require exact intended/rendered/written frame accounting and zero deadline misses, event drops, and underruns.

| Population | Result | Observations |
| --- | --- | --- |
| 5:7 at 120 BPM, 1 minute | Pass | Expected event sequence, roles, indices, coincidences, and frame accounting matched; zero underruns. |
| 15:14 at 240 BPM, 1 minute, first run | Fail | Three playback-head underruns caused skipped-frame and final-accounting mismatches. There were no event drops or deadline misses. |
| 15:14 at 240 BPM, immediate rerun | Pass | Expected event sequence and frame accounting matched; zero underruns. |

The failed run had normal device temperatures and no recorded app garbage collection. A `system_server` collection near the end is not a supported explanation because similar system activity occurred in the passing rerun. The failure is therefore retained as an unexplained intermittent underrun. A passing rerun does not supersede it; the defined one-hour dense-polyrhythm qualification remains required.

Raw artifacts:

- `benchmarks/raw/phase8/20260803T233803Z-polyrhythm-15x14-240bpm-1m`
- `benchmarks/raw/phase8/20260803T233955Z-polyrhythm-15x14-240bpm-1m-rerun`
