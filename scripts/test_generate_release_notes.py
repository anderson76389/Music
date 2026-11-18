#!/usr/bin/env python3
"""The updater must show only changes since the previous signed release."""
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(__file__))
from generate_release_notes import notes_since, summarize_subject

PREVIOUS = "a" * 40

class ReleaseNoteTests(unittest.TestCase):
    def test_scope_titles_are_short_and_in_french(self):
        self.assertEqual(
            "**Lecteur** — Corriger les transitions Son/Vidéo",
            summarize_subject("fix(player): corriger les transitions Son/Vidéo"),
        )
        self.assertEqual(
            "**Téléchargements** — Limiter aux fichiers AAC et M4A",
            summarize_subject("feat(downloads): limiter aux fichiers AAC et M4A"),
        )

    def test_ci_and_docs_commits_do_not_pollute_popup(self):
        self.assertIsNone(summarize_subject("ci(android): fix Maven mirror"))
        self.assertIsNone(summarize_subject("test: add regression coverage"))

    def test_only_commits_after_previous_apk_are_listed(self):
        calls = []
        def fake_git(*args):
            calls.append(args)
            return "fix(player): réparer le minuteur\nfeat(downloads): formats M4A et AAC"
        with patch("generate_release_notes.git", side_effect=fake_git):
            notes = notes_since(PREVIOUS, "HEAD")
        self.assertIn("## Changements de cette version", notes)
        self.assertIn("Réparer le minuteur", notes)
        self.assertIn("Formats M4A et AAC", notes)
        self.assertNotIn("ancienne version", notes)
        self.assertEqual(("log", "--first-parent", "--format=%s", f"{PREVIOUS}..HEAD"), calls[-1])

    def test_missing_previous_sha_must_not_fake_changelog(self):
        with self.assertRaises(ValueError):
            notes_since("", "HEAD")

if __name__ == "__main__":
    unittest.main()
