#!/usr/bin/env python3
from __future__ import annotations

import argparse
import math
import statistics
import wave
from array import array
from collections import deque
from pathlib import Path
from typing import Iterator


def pcm_samples(source: wave.Wave_read, channels: int) -> Iterator[float]:
    while frames := source.readframes(65_536):
        samples = array("h")
        samples.frombytes(frames)
        for index in range(0, len(samples), channels):
            yield max(abs(samples[index + channel]) for channel in range(channels)) / 32768.0


def detect_onsets(path: Path, threshold: float, minimum_distance_ms: float, window_ms: float) -> tuple[int, list[int]]:
    with wave.open(str(path), "rb") as source:
        channels = source.getnchannels()
        if source.getsampwidth() != 2:
            raise ValueError("only 16-bit PCM WAV files are supported")
        if channels < 1:
            raise ValueError("WAV file has no channels")
        rate = source.getframerate()
        window = max(1, round(rate * window_ms / 1_000))
        minimum_distance = max(1, round(rate * minimum_distance_ms / 1_000))
        squares: deque[float] = deque()
        running = 0.0
        previous_sample = 0.0
        local_values = deque(maxlen=3)
        selected: list[int] = []
        selected_strengths: list[float] = []
        for index, sample in enumerate(pcm_samples(source, channels)):
            difference = sample - previous_sample
            previous_sample = sample
            square = difference * difference
            squares.append(square)
            running += square
            if len(squares) > window:
                running -= squares.popleft()
            local_values.append(math.sqrt(running / len(squares)))
            if len(local_values) == 3:
                first, middle, last = local_values
                if middle >= threshold and middle >= first and middle > last:
                    candidate = index - 1
                    if not selected or candidate - selected[-1] >= minimum_distance:
                        selected.append(candidate)
                        selected_strengths.append(middle)
                    elif middle > selected_strengths[-1]:
                        selected[-1] = candidate
                        selected_strengths[-1] = middle
    return rate, selected


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = math.ceil(len(ordered) * fraction) - 1
    return ordered[max(0, min(index, len(ordered) - 1))]


def analyze(onsets: list[int], rate: int, interval_seconds: float, grouping: int) -> dict[str, float | int]:
    if len(onsets) < grouping + 1:
        raise ValueError("not enough onsets for analysis")
    times = [frame / rate for frame in onsets]
    expected_group_interval = interval_seconds * grouping
    grouped_errors = [
        abs((times[index + grouping] - times[index]) - expected_group_interval) / grouping
        for index in range(len(times) - grouping)
    ]
    indices = list(range(len(times)))
    mean_index = statistics.fmean(indices)
    mean_time = statistics.fmean(times)
    numerator = sum((index - mean_index) * (time - mean_time) for index, time in zip(indices, times))
    denominator = sum((index - mean_index) ** 2 for index in indices)
    fitted_interval = numerator / denominator
    fitted_endpoint_error = (fitted_interval - interval_seconds) * (len(times) - 1)
    return {
        "detected_onsets": len(onsets),
        "fitted_interval_ms": fitted_interval * 1_000,
        "fitted_bpm": 60.0 / (fitted_interval * grouping),
        "fitted_endpoint_error_ms": fitted_endpoint_error * 1_000,
        "absolute_error_p50_ms": percentile(grouped_errors, 0.50) * 1_000,
        "absolute_error_p95_ms": percentile(grouped_errors, 0.95) * 1_000,
        "absolute_error_p99_ms": percentile(grouped_errors, 0.99) * 1_000,
        "absolute_error_max_ms": max(grouped_errors) * 1_000,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Analyze metronome onsets in a PCM WAV recording.")
    parser.add_argument("wav", type=Path)
    parser.add_argument("--interval-ms", type=float, required=True)
    parser.add_argument("--grouping", type=int, default=1)
    parser.add_argument("--threshold", type=float, default=0.003)
    parser.add_argument("--minimum-distance-ms", type=float, default=45.0)
    parser.add_argument("--window-ms", type=float, default=1.5)
    args = parser.parse_args()
    if args.interval_ms <= 0 or args.grouping < 1 or args.threshold <= 0:
        parser.error("interval, grouping, and threshold must be positive")
    rate, onsets = detect_onsets(
        args.wav, args.threshold, args.minimum_distance_ms, args.window_ms
    )
    metrics = analyze(onsets, rate, args.interval_ms / 1_000, args.grouping)
    print(f"file={args.wav}")
    print(f"sample_rate={rate}")
    for name, value in metrics.items():
        print(f"{name}={value:.6f}" if isinstance(value, float) else f"{name}={value}")


if __name__ == "__main__":
    main()
