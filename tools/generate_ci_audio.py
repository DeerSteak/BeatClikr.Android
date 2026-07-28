#!/usr/bin/env python3
"""Generate non-production WAV resources used only by public CI."""

from pathlib import Path
import math
import struct
import wave


RESOURCE_NAMES = (
    "clickhi_e5",
    "clicklo_f5",
    "cowbell_gsharp3",
    "crashl_csharp3",
    "crashr_a3",
    "hatclosed_fsharp2",
    "hatopen_asharp2",
    "kick_c2",
    "ridebell_f3",
    "rideedge_dsharp3",
    "snare_d2",
    "tamb_fsharp3",
    "tomhi_d3",
    "tomlow_a2",
    "tommid_b2",
    "synth_clickhi_e5",
    "synth_clicklo_f5",
    "synth_cowbell_gsharp3",
    "synth_crashl_csharp3",
    "synth_crashr_a3",
    "synth_hatclosed_fsharp2",
    "synth_hatopen_asharp2",
    "synth_kick_c2",
    "synth_ridebell_f3",
    "synth_rideedge_dsharp3",
    "synth_snare_d2",
    "synth_tamb_fsharp3",
    "synth_tomhi_d3",
    "synth_tomlow_a2",
    "synth_tommid_b2",
)

SAMPLE_RATE = 44_100
FRAME_COUNT = 441
AMPLITUDE = 2_000


def write_placeholder(path: Path, frequency: float) -> None:
    frames = bytearray()
    for index in range(FRAME_COUNT):
        envelope = 1.0 - index / FRAME_COUNT
        sample = int(AMPLITUDE * envelope * math.sin(2.0 * math.pi * frequency * index / SAMPLE_RATE))
        frames.extend(struct.pack("<h", sample))

    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(frames)


def main() -> None:
    raw_directory = Path("app/src/main/res/raw")
    raw_directory.mkdir(parents=True, exist_ok=True)
    existing_wavs = list(raw_directory.glob("*.wav"))
    if existing_wavs:
        raise SystemExit("Refusing to overwrite existing WAV resources.")
    for index, name in enumerate(RESOURCE_NAMES):
        write_placeholder(raw_directory / f"{name}.wav", 440.0 + index * 10.0)


if __name__ == "__main__":
    main()
