# Phase 8 Timing-Budget Evidence Register

**Updated:** 2026-08-03  
**Reference:** Pixel 8a on Android 17, built-in speaker unless stated otherwise

| Budget | Current status | Evidence | Remaining qualification |
| --- | --- | --- | --- |
| TB-001 | Pass, pure layer | Current 12-hour multi-rate qualification suite | Retain as automated gate |
| TB-002 | Pass, pure layer | Long-run standard/polyrhythm and coincidence tests | Retain as automated gate |
| TB-003 | Pass, pure layer | Exhaustive-position stall recovery tests | Retain as automated gate |
| TB-004 | Partial | Two-minute 240 BPM acoustic recording has no detected misses or doubles | One-hour dense acoustic recording and manual anomaly review |
| TB-005 | Partial | Two-minute maximum-density like-timbre analysis meets limits | One hour across low, typical, and maximum density |
| TB-006 | Partial | Two-minute fitted endpoint error is within the hourly ceiling but lacks duration | One-hour fitted acoustic regression |
| TB-007 | Fail | Corrected 30-cold/30-warm release-equivalent built-in-speaker distribution and static latency decomposition | Capture a second corrected distribution, then improve safely or explicitly amend contract |
| TB-008 | Partial, standard run passes; polyrhythm smoke anomaly retained | Current screen-on one-hour maximum-density standard run has zero underruns, drops, deadline misses, or frame-accounting mismatch. The 5:7 smoke passed; the first dense 15:14 smoke had three unexplained platform underruns and its immediate rerun passed. | Complete both representative-polyrhythm long runs and the defined UI-stress run; classify recurrence, recovery correctness, and acoustic impact across the evidence set |
| TB-009 | Pass, pure/render layer | 4.1.0 phase-preserving standard and shared-origin polyrhythm boundary tests | Retain as automated gate and measure audio/visual retune skew |
| TB-010 | Pass, pure layer | Serialized randomized complete-configuration tests | Confirm zero mixed configurations in device workload |
| TB-011 | Evidence gap | No synchronized high-speed measurement | High-speed video against predicted presentation |
| TB-012 | Evidence gap | No suitable external haptic sensor | Measure externally or keep unclaimed |
| TB-013 | Evidence gap | No photodiode or equivalent capture | Measure externally or keep unclaimed |
| TB-014 | Partial | Release-equivalent 25-minute aggregate mean passes | One-hour mean and p95 under documented screen-on audio workload |
| TB-015 | Partial | Release-equivalent 30-minute PSS series shows no growth | One-hour post-warm-up series |
| TB-016 | Pass in historical reference | One-hour release-equivalent run remained at thermal status 0 | Repeat with current source and screen-on workload |
| TB-017 | Partial | One documented one-hour unplugged run passes provisional ceiling | Two more matched one-hour runs |
| TB-018 | Baseline only | Pre-Phase-3 release-equivalent comparator is pinned | Three matched before/after runs for noisy resource metrics |

“Pass” applies only to the named measurement layer. This register does not convert pure or render evidence into acoustic, visual, haptic, or flash evidence.
