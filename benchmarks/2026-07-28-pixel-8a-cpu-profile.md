# Pixel 8a CPU and Thermal Profile

**Date:** 2026-07-28  
**Device:** Pixel 8a, Android 17  
**Workload:** 240 BPM with sixteenth subdivisions for 30 minutes  
**Sampling:** ADB `top`, `dumpsys meminfo`, battery, and thermal service every 10 seconds  
**Samples:** 168 over 1,672 seconds; sampling began after workload startup  
**Power:** Connected and externally powered  

## Process results

| Metric | Mean | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| CPU | 15.41% | 20.00% | 21.09% | 24.10% |
| PSS memory | 149.74 MB | 150.08 MB | 152.73 MB | 152.96 MB |
| RSS memory | 252.84 MB | 253.15 MB | 256.15 MB | 256.34 MB |

Android `top` reports 100% per CPU core. The Pixel exposed 900% total capacity,
so the mean process value is approximately 1.71% of aggregate CPU capacity.

PSS decreased from 152.96 MB to 150.09 MB and RSS decreased from 256.34 MB to
253.16 MB. This sample does not show progressive memory growth.

## Thermal results

- Battery temperature range: 28.8–29.8°C.
- Android thermal status: 0 for every sample.
- No thermal throttling status was observed.

## Timing result under profiling load

The workload delivered all 28,800 expected callback checkpoints, but the final
strict assertion reported 182 `AudioTrack` underruns. The same 30-minute workload
previously completed with zero underruns when it was not queried every 10
seconds.

This is a failed audio stress result under intrusive diagnostic load. Repeated
`dumpsys meminfo` and thermal-service collection can perturb the process and
system, so the result does not prove ordinary playback would underrun at this
rate. It does show that the current audio path is not isolated from aggressive
diagnostic/system load.

## Follow-up

Use a lower-overhead Perfetto or Android Studio system trace for a confirmation
run. Keep the zero-underrun gate and preserve timing metrics even when an
assertion fails.

