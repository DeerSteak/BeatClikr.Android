#!/usr/bin/env python3
"""Generate non-production WAV resources used only by public CI."""

from pathlib import Path
import json
import math
import struct
import wave


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
    requirements = json.loads(Path("audio/audio-requirements.json").read_text(encoding="utf-8"))
    for index, filename in enumerate(requirements["requiredFiles"]):
        write_placeholder(raw_directory / filename, 440.0 + index * 10.0)


if __name__ == "__main__":
    main()
