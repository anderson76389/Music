#!/usr/bin/env python3
"""Generate an honest, readable release note from ONLY commits since the last APK.

Usage: python3 scripts/render_release_notes.py compare.json NOTES-VERSION.txt
compare.json is the GitHub compare API response for previous BUILD.txt SOURCE_SHA
to the exact commit being published. No stale hand-edited changelog is reused.
"""
import json
import re
import sys
from pathlib import Path

TYPES = {
    "feat": "Nouveautés",
    "fix": "Corrections",
    "perf": "Fluidité et performances",
    "refactor": "Améliorations",
    "revert": "Corrections",
}
SCOPES = {
    "player": "Lecteur",
    "video": "Son / Vidéo",
    "timeline": "Progression",
    "download": "Téléchargements",
    "downloads": "Téléchargements",
    "audio": "Audio",
    "update": "Mises à jour",
    "updater": "Mises à jour",
    "release": "Mises à jour",
    "sync": "Synchronisation",
    "backup": "Sauvegardes",
    "ui": "Interface",
    "miniplayer": "Mini-lecteur",
    "cast": "Google Cast",
    "soundproof": "SoundProof",
}


def summarize(compare: dict) -> str:
    commits = compare.get("commits") or []
    if not isinstance(commits, list) or not commits:
        raise ValueError("Aucun changement vérifié entre les deux APK")
    if compare.get("status") not in ("ahead", "diverged"):
        raise ValueError("Le nouveau commit n'est pas postérieur à l'APK précédent")

    groups = {}
    seen = set()
    for commit in commits:
        raw = commit.get("commit", {}).get("message", "").splitlines()[0].strip()
        if not raw:
            continue
        match = re.fullmatch(r"([a-z]+)(?:\(([^)]+)\))?!?:\s*(.+)", raw)
        if match:
            kind, scope, title = match.groups()
            if kind in ("test", "tests", "ci", "build", "chore", "docs", "style"):
                continue
            category = TYPES.get(kind, "Améliorations")
            topic = SCOPES.get((scope or "").lower(), (scope or "").replace("-", " ").capitalize())
        else:
            # A descriptive squash subject remains eligible, but skip merges,
            # which are maintenance operations rather than user-visible features.
            if raw.lower().startswith(("merge ", "bump ", "test ", "ci ")):
                continue
            category, topic, title = "Améliorations", "", raw
        title = re.sub(r"\s+", " ", title).strip().rstrip(".")
        if not title:
            continue
        title = title[0].upper() + title[1:]
        if len(title) > 140:
            title = title[:137].rstrip() + "…"
        item = f"{topic} : {title}" if topic else title
        if item.casefold() not in seen:
            groups.setdefault(category, []).append(item)
            seen.add(item.casefold())
    if not groups:
        groups["Maintenance"] = ["Corrections internes et stabilité"]
    lines = ["## Changements de cette version", ""]
    for category in ("Nouveautés", "Corrections", "Fluidité et performances", "Améliorations", "Maintenance"):
        if category in groups:
            lines.append(f"### {category}")
            lines.extend(f"- {item}" for item in groups[category])
            lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main() -> None:
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    output = summarize(data)
    Path(sys.argv[2]).write_text(output, encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
