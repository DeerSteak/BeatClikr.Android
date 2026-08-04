# Phase 8 Pixel 8a Haptic Alignment Observation

- Workload: audio click and vibration at 120 BPM
- Capture: phone vibrating against a desk, with the click and audible desk rattle recorded through one microphone path
- File: `vibrate take 2.wav`, retained outside the repository
- Format: 44.1 kHz, 16-bit stereo; duration 99.564 seconds
- SHA-256: `8796046dd3dd2cca01cf3bd588ae1b3ffa72b9f943bdaba926733f2403593b5a`

## Results

Frequency-separated analysis identified 196 clean click/rattle pairs. The fitted click cadence was 120.008030 BPM, and adjacent detected click intervals ranged from 499.864 to 500.862 ms.

| Desk-rattle measure after click | Median | p95 | Maximum |
| --- | ---: | ---: | ---: |
| 10% rise | 7.982 ms | 13.220 ms | 17.959 ms |
| 25% rise | 9.977 ms | 15.964 ms | 19.955 ms |
| 50% rise | 12.971 ms | 17.959 ms | 22.948 ms |
| Low-frequency peak | 18.957 ms | 24.943 ms | 28.934 ms |

The onset result passes TB-012's 25 ms p95 ceiling across the tested rise thresholds. The later low-frequency peak is not the physical onset, but its p95 also remains within 25 ms.

## Limitation

Both signals reached one microphone through acoustic and desk-coupled paths. This is an observational measurement of audible desk-rattle onset rather than a direct accelerometer or contact-sensor measurement of the vibration motor. It supports the Pixel 8a reference observation only and is not generalized to other surfaces or devices.
