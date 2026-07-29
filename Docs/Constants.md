# Constants and Helpers

## Application constants

`AppLocale` resolves the device's default locale for formatting. UI text should still come from Android string resources rather than model constants.

`MetronomeConstants` currently defines:

| Constant | Value | Use |
| --- | ---: | --- |
| `MIN_BPM` | 30 | Lower supported tempo |
| `MAX_BPM` | 240 | Upper supported tempo |
| `PLAYER_VIEW_DEFAULT_SIZE` | 80 | Standard indicator size |
| `PLAYER_VIEW_TOOLBAR_SIZE` | 30 | Compact indicator size |
| `ICON_SCALE_MIN` | 0.5 | Pulse resting scale |
| `ICON_SCALE_MAX` | 1.0 | Pulse peak scale |
| `TIMER_CHECK_INTERVAL_MS` | 1 ms | Polling interval |
| `FIRST_BEAT_DELAY_MS` | 67 ms | Startup scheduling delay |
| `LOOKAHEAD_TOLERANCE_MS` | 2 ms | Early firing window |

The timing values describe the existing polling implementation, not accuracy guarantees. They should disappear or move behind the audio backend when the sample-frame scheduler replaces polling.

## Musical helpers

- `ExactTempo` enforces the inclusive 30–240 BPM contract and converts decimal BPM to normalized rational values.
- `StandardSubdivision` and `AdditiveStepUnit` own the approved subdivision and odd-meter timing mappings.
- `AbsoluteAudioTimeline` converts exact interval rates to independently rounded absolute frame positions without accumulated remainder loss.
- `BeatPattern.accentArray` expands additive meter groups into accented ticks.
- `PolyrhythmGrid` uses GCD/LCM arithmetic to identify coincident events.
- `RampController` advances tempo after a configured number of beats.
- Room `Converters` persist UUID and musical enum values.
- Practice-history date helpers normalize values to local day boundaries.

Constants should be scoped to the subsystem that owns them. This file documents the current centralized values; it does not endorse adding unrelated globals.
