#!/usr/bin/env python3
"""Create or verify the private production-audio manifest."""

import argparse
import array
import hashlib
import json
from pathlib import Path
import sys
import wave


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def inspect_wav(path: Path) -> dict:
    try:
        with wave.open(str(path), "rb") as source:
            channels = source.getnchannels()
            sample_width = source.getsampwidth()
            frame_count = source.getnframes()
            pcm = source.readframes(frame_count)
            metadata = {
                "bank": "synthetic" if path.stem.startswith("synth_") else "acoustic",
                "channels": channels,
                "sampleRate": source.getframerate(),
                "sampleWidthBytes": sample_width,
                "frameCount": frame_count,
                "compression": source.getcomptype(),
            }
    except (EOFError, wave.Error) as error:
        raise ValueError(f"{path.name}: invalid WAV file ({error})") from error
    if metadata["compression"] == "NONE" and sample_width == 2:
        samples = array.array("h")
        samples.frombytes(pcm)
        if sys.byteorder != "little":
            samples.byteswap()
        metadata["pcmEncoding"] = "PCM_SIGNED_16_LE"
        metadata["peakSampleMagnitude"] = max((abs(sample) for sample in samples), default=0)
        leading_frames = 0
        for frame_start in range(0, len(samples), channels):
            if any(samples[frame_start : frame_start + channels]):
                break
            leading_frames += 1
        metadata["leadingSilenceFrames"] = leading_frames
    metadata["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    return metadata


def validate_format(name: str, metadata: dict, requirements: dict) -> list[str]:
    expected = requirements["format"]
    errors = []
    if metadata["compression"] != expected["compression"]:
        errors.append(f"{name}: compression must be {expected['compression']}")
    if metadata["sampleWidthBytes"] != expected["sampleWidthBytes"]:
        errors.append(f"{name}: sample width must be {expected['sampleWidthBytes']} bytes")
    if metadata["sampleRate"] not in expected["allowedSampleRates"]:
        errors.append(f"{name}: unsupported sample rate {metadata['sampleRate']}")
    if metadata["channels"] not in expected["allowedChannels"]:
        errors.append(f"{name}: unsupported channel count {metadata['channels']}")
    if metadata["frameCount"] <= 0:
        errors.append(f"{name}: WAV contains no audio frames")
    return errors


def inspect_required_files(audio_directory: Path, requirements: dict) -> tuple[dict, list[str]]:
    required = requirements["requiredFiles"]
    allowed = set(required) | set(requirements.get("allowedExtraFiles", []))
    actual = {path.name for path in audio_directory.glob("*.wav")}
    errors = [f"Missing required audio: {name}" for name in required if name not in actual]
    errors.extend(f"Unexpected WAV resource: {name}" for name in sorted(actual - allowed))
    inspected = {}
    for name in required:
        path = audio_directory / name
        if not path.is_file():
            continue
        try:
            metadata = inspect_wav(path)
            inspected[name] = metadata
            errors.extend(validate_format(name, metadata, requirements))
        except ValueError as error:
            errors.append(str(error))
    return inspected, errors


def create_manifest(path: Path, inspected: dict, asset_version: str) -> None:
    files = {}
    for name, metadata in inspected.items():
        files[name] = {key: value for key, value in metadata.items() if key != "compression"}
    manifest = {
        "schemaVersion": 2,
        "assetVersion": asset_version,
        "provenance": "Private BeatClikr source archive.",
        "license": "Proprietary BeatClikr production audio.",
        "files": files,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def compare_manifest(inspected: dict, manifest: dict, required: list[str]) -> list[str]:
    errors = []
    manifest_files = manifest.get("files", {})
    if manifest.get("schemaVersion") != 2:
        errors.append("Private manifest schemaVersion must be 2")
    if not manifest.get("assetVersion"):
        errors.append("Private manifest is missing assetVersion")
    for name in required:
        expected = manifest_files.get(name)
        if expected is None:
            errors.append(f"Private manifest is missing {name}")
            continue
        actual = inspected.get(name)
        if actual is None:
            continue
        for key in (
            "sha256",
            "bank",
            "channels",
            "sampleRate",
            "sampleWidthBytes",
            "pcmEncoding",
            "frameCount",
            "peakSampleMagnitude",
            "leadingSilenceFrames",
        ):
            if actual[key] != expected.get(key):
                errors.append(f"{name}: {key} does not match the private manifest")
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audio-dir", type=Path, default=Path("app/src/main/res/raw"))
    parser.add_argument("--requirements", type=Path, default=Path("audio/audio-requirements.json"))
    parser.add_argument("--private-manifest", type=Path, default=Path("audio/audio-manifest.private.json"))
    parser.add_argument("--create-private-manifest", action="store_true")
    parser.add_argument("--asset-version")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    requirements = load_json(args.requirements)
    inspected, errors = inspect_required_files(args.audio_dir, requirements)
    if args.create_private_manifest:
        if not args.asset_version:
            errors.append("--asset-version is required when creating a private manifest")
        if not errors:
            create_manifest(args.private_manifest, inspected, args.asset_version)
            print(f"Created private manifest: {args.private_manifest}")
    else:
        if not args.private_manifest.is_file():
            errors.append(
                f"Missing private manifest: {args.private_manifest}. "
                "See README.md#authorized-production-audio-setup."
            )
        elif not errors:
            manifest = load_json(args.private_manifest)
            errors.extend(compare_manifest(inspected, manifest, requirements["requiredFiles"]))
    if errors:
        print("Production audio validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Validated {len(inspected)} proprietary production WAV files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
