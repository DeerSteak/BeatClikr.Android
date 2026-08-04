# Phase 8 Pixel 8a 120 BPM Eighth-Note Diagnostic

- Workload: benchmark engine at 120 BPM with eighth notes for 20 minutes
- Device route: Pixel 8a built-in speaker, media volume 16/25
- Recording: GarageBand through PreSonus AudioBox and Shure MV7, approximately two inches from the speaker
- Format: stereo 44.1 kHz, 16-bit PCM WAV; 1,159.137143 seconds
- WAV SHA-256: `904c51cfa4e713ef69ab9d2dede3f35b8e0de126bb87fcb60b45ba8e79f7606d`
- Raw artifacts: `benchmarks/raw/phase8/20260804T204345Z-acoustic-diagnostic-120bpm-eighth-20m`

## Phone diagnostics

The benchmark passed 1/1 with all 4,800 expected events, zero scheduled drift, route changes, deadline misses, drops, or platform underruns. Intended, rendered, and written counts were identical at 57,603,600 frames. Callback error was 0.236 ms p50, 2.006 ms p95, 6.760 ms p99, and 33.606 ms maximum; callback arrival is not the acoustic clock.

## Acoustic rolling-tempo analysis

At threshold 0.03 the detector initially found 4,633 transients. One transient at 247.583537 seconds was an external/noise false positive: its PCM peak was 189 versus approximately 7,300 for adjacent metronome clicks, it had essentially zero waveform correlation with either click timbre, and it fell between two correctly timed clicks. Removing it left 4,632 acoustic onsets.

Across every stable four-quarter-beat window after classification:

| Metric | Result |
| --- | ---: |
| Minimum rolling tempo | 119.880391 BPM |
| Maximum rolling tempo | 120.113032 BPM |
| Windows below 119.5 BPM | 0 |
| Windows above 120.5 BPM | 0 |
| Fitted tempo | 119.995987 BPM |
| Acoustic interval error p50 | 0.011 ms |
| Acoustic interval error p95 | 0.975 ms |
| Acoustic interval error p99 | 0.975 ms |
| Acoustic interval error maximum | 0.975 ms |

The second recording does not reproduce the first file's 10 ms phase step. Its synchronized phone diagnostics were fully clean, so there is no evidence here of an app scheduler or `AudioTrack` continuity defect. The first file's isolated step remains unassigned between a nonrepeatable phone-platform event and the recording path.

The WAV contains no full-scale samples. Its file-wide peak is −0.10 dBFS, so the capture is hot but not digitally clipped.
