#!/usr/bin/env python3
"""Run from repository root; no network needed."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("release_notes", "scripts/render_release_notes.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

commits = {
    "status": "ahead",
    "commits": [
        {"commit": {"message": "fix(player): make Son/Vidéo immediate\n\nDetailed code."}},
        {"commit": {"message": "feat(download): keep highest AAC/M4A"}},
        {"commit": {"message": "ci: repair Robolectric cache"}},
        {"commit": {"message": "docs: unrelated notes"}},
    ],
}
notes = module.summarize(commits)
assert "## Changements de cette version" in notes
assert "Lecteur : Make Son/Vidéo immediate" in notes
assert "Téléchargements : Keep highest AAC/M4A" in notes
assert "Robolectric" not in notes
assert "Detailed code" not in notes
assert notes.count("\n- ") == 2
assert "Anciennes" not in notes
structured = module.summarize({
    "status": "ahead",
    "commits": [{
        "commit": {
            "message": "fix(player): technical slug\n\nRelease-Notes:\n"
                "- [Nouveautés] Boutons Son/Vidéo visibles immédiatement\n"
                "- [Corrections] Export AAC/M4A natif sans conversion\n"
                "- [Améliorations] Texte des mises à jour limité à cette version"
        }
    }],
})
assert "Boutons Son/Vidéo visibles immédiatement" in structured
assert "Export AAC/M4A natif" in structured
assert "Texte des mises à jour" in structured
assert "technical slug" not in structured
assert structured.count("\n- ") == 3
try:
    module.summarize({"status": "identical", "commits": []})
    raise AssertionError("Refused missing compare did not fail")
except ValueError:
    pass
print("PASS: release notes generated from only real commits since previous APK")
