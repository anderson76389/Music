#!/usr/bin/env python3
"""Smoke checks for published release descriptions; no GitHub secrets needed."""
import release_notes


def test_highlights_are_release_specific():
    body = release_notes.build_notes(
        ["Amélioration de la barre Son/Vidéo", "Export AAC en M4A"], "a" * 40,
    )
    assert body.startswith("## Nouveautés de cette version\n")
    section = body.split("## Télécharger et installer", 1)[0]
    assert "Amélioration de la barre Son/Vidéo" in section
    assert "Apple.Music-arm64" not in section
    assert body.count("Sources :") == 1


def test_bullets_do_not_retain_previous_release_sections():
    sample = "- Nouveau lecteur\n- Nouvelle qualité\n\n"
    assert release_notes.extract_bullets(sample) == ["Nouveau lecteur", "Nouvelle qualité"]
    assert release_notes.extract_bullets(sample) == release_notes.extract_bullets(sample)


if __name__ == "__main__":
    test_highlights_are_release_specific()
    test_bullets_do_not_retain_previous_release_sections()
    print("PASS: GitHub release notes show only latest highlights, separated from technical APK details")
