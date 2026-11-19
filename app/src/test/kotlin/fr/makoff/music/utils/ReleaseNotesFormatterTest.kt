package fr.makoff.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesFormatterTest {
    @Test fun displaysOnlyCurrentReleaseNotesNotApkInstructions() {
        val body = """
            ## Apple Music pour Android
            - Apple.Music.apk : APK universelle
            - APK signés et somme SHA-256
            ## Modifications de cette version
            - **Son/Vidéo :** boutons visibles dès l'ouverture
            - **Audio :** AAC et M4A
            Sources : `0123456`
        """.trimIndent()
        assertEquals(listOf("Son/Vidéo : boutons visibles dès l'ouverture", "Audio : AAC et M4A"),
            ReleaseNotesFormatter.changes(body))
    }

    @Test fun doesNotRecycleOldGenericReleaseOrUnavailableNotes() {
        assertTrue(ReleaseNotesFormatter.changes("## Apple Music pour Android\n- Anciennes fonctions").isEmpty())
        assertTrue(ReleaseNotesFormatter.changes("").isEmpty())
    }

    @Test fun stopsAtNextSectionAndLimitsExcessiveNotes() {
        val body = "## Modifications de cette version\n" +
            (1..20).joinToString("\n") { "- Correction $it" } +
            "\n## Notes historiques\n- À ignorer"
        assertEquals(12, ReleaseNotesFormatter.changes(body).size)
    }
}
