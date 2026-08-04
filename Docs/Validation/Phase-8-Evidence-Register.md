# Phase 8 Timing-Budget Evidence Register

**Updated:** 2026-08-04 **Reference:** Pixel 8a on Android 17, built-in speaker unless stated otherwise

| Budget | Current status | Evidence | Remaining qualification |
| --- | --- | --- | --- |
| TB-001 | Pass, pure layer | Current 12-hour multi-rate qualification suite | Retain as automated gate |
| TB-002 | Pass, pure layer | Long-run standard/polyrhythm and coincidence tests | Retain as automated gate |
| TB-003 | Pass, pure layer | Exhaustive-position stall recovery tests | Retain as automated gate |
| TB-004 | Pass | At least 20 minutes each at low, typical, and maximum density had zero classified misses or doubles, backed by clean one-hour maximum-density render evidence | Retain recordings, classifications, and render artifacts |
| TB-005 | Pass | Low, typical, and maximum-density acoustic populations pass p50/p95/p99/max limits | Retain recordings and threshold classifications |
| TB-006 | Pass at application layer; physical observation recorded | TB-001 passes, and every acoustic population reports fitted drift with the uncalibrated AudioBox/GarageBand clock disclosed | No fixed physical-drift claim without a calibrated reference clock |
| TB-007 | Pass | Corrected 30-cold/30-warm release-equivalent built-in-speaker distribution meets the amended p50 ≤ 250 ms, p95 ≤ 300 ms, and maximum ≤ 500 ms gate | Retain the corrected same-clock test as the automated gate |
| TB-008 | Pass at application/render layer with controlled acoustic review | The one-hour standard, 5:7, and 720-cycle UI-stress runs were fully clean. Controlled 30/120 BPM acoustic diagnostics were clean; maximum-density acoustic timing passed locally. Platform anomalies remain classified separately. | Retain the initial 120 BPM phase step and platform underruns without promoting them to application failures |
| TB-009 | Pass, pure/render layer | 4.1.0 phase-preserving standard and shared-origin polyrhythm boundary tests | Retain as automated gate; no physical retune-alignment claim |
| TB-010 | Pass, pure and device render layers | Serialized randomized tests plus 720 same-session UI cycles with every complete configuration observed | Retain as automated gates |
| TB-011 | Observational pass for approved scope | At 120 BPM and 60 Hz, standard and 6:4 polyrhythm video offsets matched the hand-clap camera A/V offset; corrected residuals were approximately zero | No separate physical retune-alignment claim |
| TB-012 | Observational pass for approved scope | A 99.564-second desk-coupled recording produced 196 click/rattle pairs; haptic-onset p95 ranged from 13.220 to 17.959 ms across rise thresholds | Single-microphone desk-rattle measurement, not a direct vibration-motor sensor |
| TB-013 | Observational pass for approved scope | At 120 BPM, high-speed video showed the flashlight leading recorded audio by approximately 12 frames at 240 fps, matching the clap-calibrated camera A/V offset | Single manual observation does not establish p95; no route-generalized claim |
| TB-014 | Pass | The clean 3,450-second aggregate mean was 17.54%; two diagnostic populations reported p95 CPU of 18.18% and 20.00%, both below 35% | Sampled runs qualify only the CPU distribution because their polling invalidated audio-continuity evidence |
| TB-015 | Pass | Current one-hour warm-to-near-end PSS growth was 9.74 MiB against the 10 MiB ceiling | Retain as regression evidence |
| TB-016 | Pass | Current one-hour screen-on run remained at thermal status 0 | Retain as regression evidence |
| TB-017 | Pass | Controlled one-hour sniff check passes at 5.97 percentage points/hour; earlier release comparator also passed | None for the qualitative battery-efficiency claim |
| TB-018 | Pass for current release baseline | Completed Phase 8 timing, acoustic, resource, thermal, battery, UI, and lifecycle evidence is the baseline for the next sensitive change | One future matched sniff check; confirm only anomalies or near-budget results |

“Pass” applies only to the named measurement layer. This register does not convert pure or render evidence into acoustic, visual, haptic, or flash evidence.

Bluetooth lifecycle behavior passed observationally: the latency warning appeared, route removal stopped playback, and restart remained explicit. Application scheduling requirements still apply, but absolute Bluetooth presentation latency is excluded because the Android transport, codec, radio conditions, and receiving device dominate it. USB audio and analog line/headphone output were unavailable for physical latency measurement; their adapter, DAC, driver, and buffering remain route-dependent. No built-in-speaker timing result is generalized to Bluetooth, USB, or analog output.
