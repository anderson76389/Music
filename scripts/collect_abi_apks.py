#!/usr/bin/env python3
"""Validate ABI-specific Music APKs before a release can publish them.

Reads Gradle's output metadata rather than guessing filename order. The same
validation is used for PR debug builds and signed release publication.
"""
import argparse
import json
import shutil
import zipfile
from pathlib import Path

ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def collect(input_dir: Path, output_dir: Path, universal: Path | None) -> None:
    metadata = json.loads((input_dir / "output-metadata.json").read_text())
    assert metadata.get("elements"), "Gradle produced no APK outputs"

    by_abi = {}
    for item in metadata["elements"]:
        filters = item.get("filters") or []
        abis = [str(f.get("value")) for f in filters if str(f.get("filterType")).upper() == "ABI"]
        if len(abis) != 1 or abis[0] not in ABIS:
            continue
        abi = abis[0]
        assert abi not in by_abi, f"Duplicate Gradle output for {abi}"
        source = input_dir / item["outputFile"]
        assert source.is_file(), f"Missing APK for {abi}: {source}"
        with zipfile.ZipFile(source) as archive:
            packaged_abis = {
                name.split("/")[1]
                for name in archive.namelist()
                if name.startswith("lib/") and name.endswith(".so") and len(name.split("/")) >= 3
            }
        assert packaged_abis == {abi}, (
            f"{source.name} must contain only {abi} native libraries, got {packaged_abis}"
        )
        by_abi[abi] = source

    assert set(by_abi) == set(ABIS), f"Expected one APK for each {ABIS}, got {set(by_abi)}"
    output_dir.mkdir(parents=True, exist_ok=True)
    universal_bytes = universal.stat().st_size if universal else None
    for abi, source in sorted(by_abi.items()):
        size = source.stat().st_size
        assert size > 0
        if universal_bytes:
            assert size < universal_bytes, (
                f"{source.name} ({size}) must be smaller than universal ({universal_bytes})"
            )
        destination = output_dir / f"Apple.Music-{abi}.apk"
        shutil.copyfile(source, destination)
        saved = f" ({(1 - size / universal_bytes) * 100:.1f}% less than universal)" if universal_bytes else ""
        print(f"COMPACT_APK_OK {abi}: {size:,} bytes{saved} -> {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input_dir", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--universal", type=Path)
    args = parser.parse_args()
    collect(args.input_dir, args.output_dir, args.universal)
