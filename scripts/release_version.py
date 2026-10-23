#!/usr/bin/env python3
"""Allocate a greater Android version than the actual previously published APK."""
import argparse
import re
import subprocess
from pathlib import Path


def next_version(run_number: int, previous_code: int) -> tuple[int, str]:
    code = max(10000 + run_number, previous_code + 1)
    if not 10000 < code <= 2100000000:
        raise ValueError("Numéro de version Android invalide")
    return code, f"13.7.0.{code - 10000}"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-number", required=True, type=int)
    parser.add_argument("--previous-apk", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--github-env", required=True)
    args = parser.parse_args()
    badging = subprocess.check_output([args.aapt, "dump", "badging", args.previous_apk], text=True)
    match = re.search(r"^package: .*versionCode='(\d+)'", badging, re.MULTILINE)
    if not match:
        raise SystemExit("Impossible de lire la version de l’APK précédent")
    code, name = next_version(args.run_number, int(match.group(1)))
    with Path(args.github_env).open("a") as output:
        output.write(f"MUSIC_VERSION_CODE={code}\nMUSIC_VERSION_NAME={name}\nRELEASE_TAG=v{name}\n")
    print(f"Prochaine version : {name} (code {code})")
