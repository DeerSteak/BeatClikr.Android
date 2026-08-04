# Phase 8 Pixel 8a Battery Qualification

- Run number:
- Date and observation window:
- Source commit and working-tree state:
- Device build fingerprint:
- APK and test APK SHA-256:
- ADB transport: one wireless transport
- Power: AC false; USB false; wireless charging false
- Route: built-in speaker
- Workload: fixed 240 BPM sixteenth notes for 60 minutes
- Display: screen on; adaptive brightness off; brightness 128/255
- Media: volume 6/25; unmuted
- Connectivity: Wi-Fi and wireless debugging on
- Raw artifacts:
- Exact command:

## Results

| Measurement | Start | End | Normalized result |
| --- | ---: | ---: | ---: |
| Displayed battery level |  |  | percentage points/hour |
| Charge counter |  µAh |  µAh | µAh/hour and starting charge/hour |
| Battery temperature |  °C |  °C | change |
| Android thermal status |  |  | maximum |

Record intended, rendered, and written frames; deadline misses; dropped or duplicate events; mixed configurations; platform underruns and skipped frames; route changes; and actual workload duration. Classify isolated platform underruns under TB-008 rather than converting them into battery exceptions.

## Verdict

TB-017 passes this repetition when consumption is no more than six displayed percentage points per hour and the required settings remained matched. Report charge-counter consumption separately because the displayed percentage can remain on the 100% plateau.

## Restoration

Record restoration of brightness mode and value, volume, timeout, keep-awake, connectivity, and any other changed setting.
