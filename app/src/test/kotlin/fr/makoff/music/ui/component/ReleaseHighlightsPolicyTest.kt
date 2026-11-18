package fr.makoff.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseHighlightsPolicyTest {
    @Test fun popupUsesOnlyCurrentVersionBullets() {
        val body = """
            ## Changements de cette version

            - **Lecteur** — Correction du minuteur
            - **Vidéo** — Affichage immédiat des boutons

            ## Installer Music

            - **Apple.Music-arm64-v8a.apk** : pour les téléphones 64 bits.
            - **SIGNATURE.txt** et **SHA256SUMS.txt** pour les APK.
        """.trimIndent()
        val notes = ReleaseHighlightsPolicy.items(body)
        assertEquals(2, notes.size)
        assertEquals("Lecteur — Correction du minuteur", notes[0])
        assertEquals("Vidéo — Affichage immédiat des boutons", notes[1])
        assertFalse(notes.any { it.contains(".apk") })
    }

    @Test fun legacyReleaseDoesNotPassApkInstallDetailsAsChanges() {
        val notes = ReleaseHighlightsPolicy.items("""
            ## Apple Music pour Android
            - **Apple.Music.apk** : APK universelle
            - **Metrolist.apk** : ancienne version
            - Correction du lecteur vidéo
        """.trimIndent())
        assertEquals(listOf("Correction du lecteur vidéo"), notes)
    }

    @Test fun emptyReleaseHasNoFabricatedChange() {
        assertTrue(ReleaseHighlightsPolicy.items("").isEmpty())
    }
}
