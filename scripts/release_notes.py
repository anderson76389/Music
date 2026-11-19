#!/usr/bin/env python3
"""Publish ONLY changes since the previous signed Music release.

The hand-written CHANGEMENTS-VERSION.txt is used only when it changed in the
current release range. Otherwise derive notes from commits in that range.
This avoids carrying an old changelog into every future update popup.
"""
from __future__ import annotations
import argparse
import pathlib
import re
import subprocess


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.DEVNULL).strip()


def notes(previous: str, root: pathlib.Path) -> str:
    text = (root / "CHANGEMENTS-VERSION.txt").read_text(encoding="utf-8").strip()
    if previous:
        try:
            old = git("show", f"{previous}:CHANGEMENTS-VERSION.txt")
        except subprocess.CalledProcessError:
            old = None
        # If the file is unchanged, its content is historical and should NOT
        # reappear in the popup for a new version.
        if old == text:
            text = ""
    if not text:
        try:
            subjects = git("log", "--no-merges", "--format=%s",
                           *( [f"{previous}..HEAD"] if previous else ["-12"] )).splitlines()
        except subprocess.CalledProcessError:
            subjects = []
        clean = []
        for subject in subjects:
            title = re.sub(r"^(fix|feat|perf|refactor|docs|test|ci|chore)(\([^)]*\))?:\s*", "", subject, flags=re.I).strip()
            if not title or title.lower().startswith(("merge ", "release ")):
                continue
            if title not in clean:
                clean.append(title)
        text = "\n".join(f"- {entry}" for entry in clean[:12])
    if not text:
        text = "- Améliorations de stabilité et de compatibilité."
    text = "\n".join(line.strip() for line in text.splitlines() if line.strip())
    return "## Modifications de cette version\n\n" + text + "\n"


if __name__ == "__main__":
    cli = argparse.ArgumentParser()
    cli.add_argument("--previous", default="")
    cli.add_argument("--output", required=True)
    args = cli.parse_args()
    pathlib.Path(args.output).write_text(
        notes(args.previous, pathlib.Path(__file__).resolve().parents[1]),
        encoding="utf-8",
    )
