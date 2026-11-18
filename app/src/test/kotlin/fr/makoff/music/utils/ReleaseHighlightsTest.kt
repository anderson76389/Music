package fr.makoff.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseHighlightsTest {
    @Test fun onlyLatestVersionHighlightsAppearInPopup() {
        val notes = """
            ## Nouveautés de cette version
            - **Son/Vidéo** visible immédiatement.
            - **AAC** téléchargé en qualité maximale.

            ## Télécharger et installer
            - Apple.Music-arm64-v8a.apk
            - Signature vérifiée
            Sources : abc123
        """.trimIndent()
        assertEquals(
            listOf("Son/Vidéo visible immédiatement.", "AAC téléchargé en qualité maximale."),
            ReleaseHighlights.forPopup(notes),
        )
    }

    @Test fun oldGenericReleasesNeverShowAccumulatedHistoryInPopup() {
        assertTrue(ReleaseHighlights.forPopup(
            "## Apple Music pour Android\nAnciennes mises à jour historiques et détails techniques."
        ).isEmpty())
    }

    @Test fun noReleaseNotesDoNotFabricateChanges() {
        assertTrue(ReleaseHighlights.forPopup("").isEmpty())
        assertTrue(ReleaseHighlights.forPopup("Sources : abc123").isEmpty())
    }
}
