# Pixel 8a Battery Profile

**Date:** 2026-07-28  
**Device:** Pixel 8a, Android 17  
**Build:** Debug instrumentation build  
**Route:** Built-in speaker  
**Workload:** 240 BPM with sixteenth subdivisions  
**Instrumented workload duration:** 3,600.648 seconds  
**Battery observation window:** 3,807 seconds

## Conditions

The phone ran physically unplugged and remained connected through Android Wireless Debugging. The display stayed awake at manual brightness 128/255. The speaker media stream was unmuted at index 9/25, corresponding to the user's approximately 50% volume-slider setting. Wi-Fi and the normal cellular radios remained enabled.

The battery snapshot was captured immediately before instrumentation. The end snapshot was captured after the test result was collected, so the observation window includes approximately 3.2 additional minutes of screen-on idle time. Rates below are normalized to the complete 3,807-second battery observation window.

## Battery and thermal results

| Metric | Start | End | Normalized result |
| --- | ---: | ---: | ---: |
| Android battery level | 100% | 97% | 2.84 percentage points/hour |
| Charge counter | 3,924,000 µAh | 3,698,000 µAh | 213.77 mAh/hour |
| Charge-counter fraction of starting charge | — | — | 5.45%/hour |
| Battery temperature | 29.9°C | 28.6°C | No increase |
| Current thermal status | 0 | 0 | No escalation |

The run meets the provisional TB-017 limit of no more than six percentage points per hour under documented reference settings. The charge-counter result is more informative than the displayed level because the run began on the 100% reporting plateau.

## Audio and timing results

| Metric | Result |
| --- | ---: |
| Events | 57,600 |
| Scheduled drift | 0 ms |
| Callback interval error p50 | 0.301 ms |
| Callback interval error p95 | 1.115 ms |
| Callback interval error p99 | 2.310 ms |
| Callback interval error maximum | 89.220 ms |
| `AudioTrack` underruns | 4 |
| Rendered chunks | 1,439,915 |
| Written frames | 172,789,800 |

All expected callbacks completed and the scheduled timeline did not drift. The four `AudioTrack` underruns fail the proposed TB-008 zero-underrun gate. This is baseline evidence for scheduler/render hardening in Phases 2 and 3; it does not invalidate the battery-consumption measurement.

## Memory

The warm start snapshot reported 155,643 KiB total proportional set size and 261,104 KiB total resident set size. Android's retained two-hour process statistics reported 152 MiB minimum, average, and maximum proportional set size over two samples for the relevant process UID, with 147 MiB unique set size and 255 MiB resident set size. This supports the earlier observation that memory remained flat, although it is not a dense memory time series.

## Restoration

After collection, the phone was restored to adaptive brightness, brightness value 11, a 30-minute screen timeout, and muted media—the settings recorded before the test.

Raw battery, thermal, power, process, memory, and stress-log artifacts are stored under `benchmarks/raw/2026-07-28-pixel-8a-battery/`.
