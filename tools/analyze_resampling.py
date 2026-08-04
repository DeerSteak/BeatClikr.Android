#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import math
import sys
import wave
from array import array
from pathlib import Path


def read_mono(path: Path) -> tuple[int, list[float]]:
    with wave.open(str(path), "rb") as source:
        channels = source.getnchannels()
        if source.getsampwidth() != 2 or channels < 1:
            raise ValueError(f"{path}: expected 16-bit PCM with at least one channel")
        rate = source.getframerate()
        raw = array("h")
        raw.frombytes(source.readframes(source.getnframes()))
    samples = [
        sum(raw[index + channel] for channel in range(channels)) / (channels * 32768.0)
        for index in range(0, len(raw), channels)
    ]
    if not samples:
        raise ValueError(f"{path}: empty audio")
    return rate, samples


def linear_resample(source: list[float], source_rate: int, target_rate: int) -> list[float]:
    if source_rate == target_rate:
        return list(source)
    target_size = max(1, len(source) * target_rate // source_rate)
    result = []
    for index in range(target_size):
        position = index * source_rate / target_rate
        left = min(int(position), len(source) - 1)
        right = min(left + 1, len(source) - 1)
        fraction = position - left
        result.append(source[left] * (1.0 - fraction) + source[right] * fraction)
    return result


def onset_frame(samples: list[float]) -> int:
    if len(samples) < 2:
        return 0
    differences = [abs(samples[index] - samples[index - 1]) for index in range(1, len(samples))]
    peak = max(differences)
    threshold = peak * 0.1
    return next(index + 1 for index, value in enumerate(differences) if value >= threshold)


def transient_metrics(samples: list[float], rate: int, onset: int) -> tuple[float, float, float]:
    window = samples[onset:min(len(samples), onset + max(2, rate // 50))]
    peak = max(abs(value) for value in window)
    rms = math.sqrt(sum(value * value for value in window) / len(window))
    differences = [window[index] - window[index - 1] for index in range(1, len(window))]
    derivative_rms = math.sqrt(sum(value * value for value in differences) / len(differences))
    return peak, peak / rms if rms else 0.0, derivative_rms


def normalized_rmse(reference: list[float], candidate: list[float]) -> float:
    count = min(len(reference), len(candidate))
    peak = max(abs(value) for value in reference[:count])
    if count == 0 or peak == 0:
        return 0.0
    error = math.sqrt(sum((reference[index] - candidate[index]) ** 2 for index in range(count)) / count)
    return error / peak


def analyze(path: Path, target_rate: int) -> dict[str, str | int | float]:
    source_rate, source = read_mono(path)
    converted = linear_resample(source, source_rate, target_rate)
    roundtrip = linear_resample(converted, target_rate, source_rate)
    source_onset = onset_frame(source)
    target_onset = onset_frame(converted)
    source_peak, source_crest, source_derivative = transient_metrics(source, source_rate, source_onset)
    target_peak, target_crest, target_derivative = transient_metrics(converted, target_rate, target_onset)
    peak_ratio = target_peak / source_peak if source_peak else 1.0
    return {
        "file": str(path),
        "source_rate": source_rate,
        "target_rate": target_rate,
        "source_frames": len(source),
        "target_frames": len(converted),
        "onset_shift_ms": target_onset * 1_000 / target_rate - source_onset * 1_000 / source_rate,
        "peak_delta_db": 20 * math.log10(peak_ratio) if peak_ratio > 0 else float("-inf"),
        "crest_ratio": target_crest / source_crest if source_crest else 1.0,
        "derivative_rms_ratio": target_derivative / source_derivative if source_derivative else 1.0,
        "roundtrip_normalized_rmse": normalized_rmse(source, roundtrip),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Characterize BeatClikr linear WAV resampling.")
    parser.add_argument("wav", type=Path, nargs="+")
    parser.add_argument("--target-rate", type=int, action="append", required=True)
    args = parser.parse_args()
    if any(rate <= 0 for rate in args.target_rate):
        parser.error("target rates must be positive")
    rows = [analyze(path, rate) for path in args.wav for rate in args.target_rate]
    writer = csv.DictWriter(sys.stdout, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)


if __name__ == "__main__":
    main()
