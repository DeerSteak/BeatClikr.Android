# Phase 8 Regression Baseline

The completed Pixel 8a Phase 8 qualification is the TB-018 baseline for the next timing- or resource-sensitive change. The pre-Phase-3 `release/4.1.0` comparator remains historical context and does not require a retroactive matched campaign.

The baseline comprises the versioned Phase 8 evidence register and the dated scheduler, standard render, polyrhythm, UI interaction, startup, resampling, battery, resource, acoustic, visual-alignment, and physical-lifecycle records. Each record pins its measurement layer, device settings, command or observation method, limitations, and raw artifacts where available.

Future comparisons use the same workload and settings as the relevant baseline record. One matched sniff check is sufficient when it remains comfortably within the fixed budget and shows no operational anomaly. Repeat a result when it approaches a fixed ceiling, shows an unexpected nonzero delta, or suggests an operational regression. Deterministic invariants—including application deadline misses, drops, duplicate events, mixed configurations, and incorrect recovery—retain zero tolerance.

A fixed-budget violation blocks release. A noisy metric that still passes its ceiling blocks only after the suspected regression repeats in a confirmation run. Physical transport latency and uncalibrated recorder-clock drift remain observational. Application scheduling remains in scope on every route, but absolute Bluetooth, USB, and analog presentation latency is route-dependent and excluded from fixed claims unless that exact route is measured.
