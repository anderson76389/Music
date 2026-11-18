#!/usr/bin/env python3
"""Notes de version exactes : seuls les commits depuis l'APK précédente.

Le release body est utilisé directement par le popup Android. Ne jamais y
réinjecter une liste statique de nouveautés anciennes.
"""
import argparse
import re
import subprocess

def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True, encoding="utf-8").strip()

def summarize_subject(subject: str) -> str | None:
    subject = subject.strip()
    if not subject or subject.startswith("Merge "):
        return None
    match = re.match(r"(?i)^(feat|fix|perf|refactor|chore|build|ci|docs|test|style)(?:\(([^)]+)\))?!?:\s*(.+)$", subject)
    if not match:
        return subject.rstrip(".")
    kind, scope, details = match.groups()
    # Details of CI/tooling should not pollute the user-facing popup unless
    # the release genuinely contains no user-facing changes.
    if kind.lower() in {"docs", "test", "ci", "build", "chore", "style"}:
        return None
    details = details.strip().rstrip(".")
    if not details:
        return None
    area = (scope or {
        "fix": "Correctif", "feat": "Nouveauté", "perf": "Performance",
        "refactor": "Amélioration",
    }[kind.lower()]).replace("/", " / ").replace("_", " ")
    return f"**{area[0].upper() + area[1:]}** — {details[0].upper() + details[1:]}"

def notes_since(previous: str, current: str = "HEAD") -> str:
    if not re.fullmatch(r"[0-9a-fA-F]{40}", previous or ""):
        raise ValueError("SHA de l'APK précédente absent ou invalide")
    git("cat-file", "-e", f"{previous}^{{commit}}")
    git("merge-base", "--is-ancestor", previous, current)
    raw = git("log", "--first-parent", "--format=%s", f"{previous}..{current}")
    subjects = [s for s in raw.splitlines() if s.strip()]
    changes = list(dict.fromkeys(filter(None, map(summarize_subject, subjects))))
    if not changes and subjects:
        changes = ["**Maintenance** — Améliorations internes de cette version"]
    if not changes:
        changes = ["Aucun changement de code depuis la version précédente"]
    return "## Changements de cette version\n\n" + "\n".join(f"- {item}" for item in changes) + "\n"

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--previous", required=True)
    parser.add_argument("--current", default="HEAD")
    args = parser.parse_args()
    print(notes_since(args.previous, args.current), end="")
