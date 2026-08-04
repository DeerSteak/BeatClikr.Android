# Initial Emulator and Pixel 8a Baselines

These 2026-07-28 measurements were exploratory baselines collected before the Phase 3 renderer and Phase 8 qualification. They remain historical context rather than current release gates.

## Findings

- Android 17 emulator correctness runs decoded both sound banks and exercised dense standard and representative polyrhythm scheduling. Callback measurements were software evidence only and did not establish physical audio quality.
- The initial Pixel 8a dense run completed without scheduled drift. A 30-minute scheduler stress population reported 1.247 ms callback p95 and 3.650 ms p99; callback arrival was not treated as acoustic onset.
- A two-minute, built-in-speaker recording at 240 BPM with sixteenth notes detected 1,982 onsets with no missing or extra events. It fitted 240.004918 BPM, with 2.494 ms p95, 2.506 ms p99, and 5 ms maximum absolute inter-onset error.
- The first sampled CPU profile averaged 15.41% of one core, with 20.00% p95, 21.09% p99, and 24.10% maximum. Intrusive polling induced underruns, so the distribution became diagnostic evidence rather than continuity qualification.
- A lower-overhead 25-minute profile averaged 20.94% of one core with no scheduled drift or underruns. It did not produce CPU percentiles.
- The initial one-hour unplugged battery observation consumed 2.84 displayed percentage points per hour, or 5.45% of starting charge per hour, without thermal escalation. Four platform underruns made it anomaly evidence rather than the final release result.
- The pinned `release/4.1.0` comparator averaged 14.67% CPU over 25 minutes and completed its one-hour battery workload at 2.87 displayed percentage points per hour with zero scheduled drift and zero underruns. Phase 8 later superseded it as the active baseline.
- Early startup tables that mixed relative frames and absolute clocks were invalidated. The corrected release-equivalent Phase 8 startup population replaced them.

The removed detailed reports, raw dumps, and initial acoustic WAV remain recoverable from repository history where they had been committed.
