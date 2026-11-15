#!/usr/bin/env python3
"""Regression tests for compact APK asset collection without Android SDK."""
import json
import tempfile
import zipfile
from pathlib import Path

from collect_abi_apks import ABIS, collect


def test_compact_assets() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        inputs, output = root / "inputs", root / "published"
        inputs.mkdir()
        universal = root / "universal.apk"
        universal.write_bytes(b"u" * 2048)
        elements = []
        for abi in ABIS:
            name = f"app-{abi}-gms-release.apk"
            with zipfile.ZipFile(inputs / name, "w") as archive:
                archive.writestr(f"lib/{abi}/libdummy.so", b"ELF" * 4)
            elements.append({
                "outputFile": name,
                "filters": [{"filterType": "ABI", "value": abi}],
            })
        (inputs / "output-metadata.json").write_text(json.dumps({"elements": elements}))
        collect(inputs, output, universal)
        assert sorted(p.name for p in output.iterdir()) == sorted(f"Apple.Music-{abi}.apk" for abi in ABIS)

        # Refuse an apparently compact build if another architecture sneaks in.
        with zipfile.ZipFile(inputs / "app-arm64-v8a-gms-release.apk", "a") as archive:
            archive.writestr("lib/x86_64/libwrong.so", b"ELF")
        try:
            collect(inputs, output, universal)
        except AssertionError as error:
            assert "must contain only" in str(error)
        else:
            raise AssertionError("Mixed-ABI compact APK was accepted")


if __name__ == "__main__":
    test_compact_assets()
    print("PASS: compact APK asset collector and mixed-ABI rejection")
