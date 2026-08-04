# Phase 8 Pixel 8a Physical Lifecycle Observations

The product owner completed the Phase 8 physical lifecycle checklist on the Pixel 8a and confirmed the expected behavior:

- Competing media paused or ducked when BeatClikr acquired long-duration audio focus.
- Transient and permanent focus loss stopped BeatClikr without silent resumption.
- Releasing focus allowed the competing media player to recover.
- Bluetooth displayed the latency warning; route removal stopped playback and required explicit restart.
- Wired and USB physical latency observations were unavailable because suitable phone-connected output hardware was not available; automated route-loss coverage remains in place.
- The torch failsafe turned the torch off after explicit stop, app backgrounding, interruption, and forced failure where practical.

These are product-owner physical observations rather than automated instrumentation results. They close the Phase 8 lifecycle checklist on the reference device and do not generalize behavior to all Android hardware, media applications, Bluetooth devices, USB accessories, or analog adapters. Application scheduling remains governed by its frame-domain contracts on every route. Absolute Bluetooth, USB, and analog presentation latency is excluded because the transport, codec, adapter or DAC, driver, buffering, and receiving device are route-dependent.
