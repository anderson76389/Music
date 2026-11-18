#!/usr/bin/env python3
"""Write concise, release-specific French notes for Music's signed APK.

Only use curated CHANGEMENTS-VERSION.txt when it was changed since the
previous published SHA. Otherwise derive notes from actual new commits.
Never copy last release's note text into the next pop-up.
"""
import argparse
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[1]
CHANGELOG = "CHANGEMENTS-VERSION.txt"
TITLE = "## Nouveautés de cette version"


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def extract_bullets(text: str) -> list[str]:
    lines = []
    for line in text.splitlines():
        line = line.strip()
        if line.startswith(("- ", "* ")):
            line = line[2:].strip()
        if not line or line.startswith("#"):
            continue
        if len(line) > 230:
            line = line[:227].rstrip() + "..."
        if line not in lines:
            lines.append(line)
    return lines[:8]


def changes_since(previous: str | None, current: str) -> list[str]:
    previous = previous or ""
    valid = bool(re.fullmatch(r"[0-9a-fA-F]{40}", previous))
    ancestor = False
    if valid:
        ancestor = subprocess.run(
            ["git", "merge-base", "--is-ancestor", previous, current],
            cwd=ROOT, check=False, capture_output=True,
        ).returncode == 0

    if ancestor:
        changed = subprocess.run(
            ["git", "diff", "--quiet", previous, current, "--", CHANGELOG],
            cwd=ROOT, check=False,
        ).returncode != 0
        if changed:
            curated = extract_bullets((ROOT / CHANGELOG).read_text(encoding="utf-8"))
            if curated:
                return curated

    # Correct even when the curated file wasn't touched: only inspect commits
    # that belong to THIS published version, never accumulated older notes.
    range_spec = f"{previous}..{current}" if ancestor else "-1"
    output = git("log", "--no-merges", "--format=%s", range_spec)
    subjects = []
    for raw in output.splitlines():
        subject = re.sub(r"^(feat|fix|perf|refactor)(\([^)]*\))?!?:\s*", "", raw, flags=re.I).strip()
        if subject == raw and re.match(r"^(chore|ci|docs|test|build)(\(|:)", raw, re.I):
            continue
        if subject and subject not in subjects:
            subjects.append(subject)
    return subjects[:8] or ["Maintenance et corrections techniques de cette version."]


def build_notes(highlights: list[str], source: str) -> str:
    bullets = "\n".join(f"- {line}" for line in highlights)
    return f"""## Nouveautés de cette version

{bullets}

## Télécharger et installer

- **Apple.Music-arm64-v8a.apk** : recommandé sur les téléphones Android 64 bits, dont HONOR Magic V6.
- **Apple.Music.apk** : version universelle. Les autres APK correspondent à leurs architectures.
- Les mises à jour utilisent la même signature et remplacent l'installation existante.
- Les signatures et sommes SHA-256 sont fournies séparément.

La compilation et les tests automatisés sont vérifiés avant publication. Les essais réels sur les appareils restent distincts.

Sources : `{source}`
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--previous-source", default="")
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    Path(args.output).write_text(
        build_notes(changes_since(args.previous_source, args.source_sha), args.source_sha),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
