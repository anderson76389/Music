package fr.makoff.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTextTest {
    @Test fun onlyCurrentVersionChangesAreDisplayed() {
        val body = """
            ## Changements de cette version

            ### Nouveautés
            - Boutons Son/Vidéo présents dès l'ouverture

            ### Corrections
            - Téléchargements AAC/M4A en meilleure qualité
            - Fenêtre des mises à jour plus lisible

            ## Apple Music pour Android
            - Anciennes notes techniques et APK à ne pas afficher
            Sources : 123456
        """.trimIndent()
        assertEquals(
            listOf(
                ReleaseChangeGroup("Nouveautés", listOf("Boutons Son/Vidéo présents dès l'ouverture")),
                ReleaseChangeGroup("Corrections", listOf(
                    "Téléchargements AAC/M4A en meilleure qualité",
                    "Fenêtre des mises à jour plus lisible",
                )),
            ),
            currentReleaseChanges(body),
        )
    }

    @Test fun olderReleasesDoNotInventOrRecycleChanges() {
        assertTrue(currentReleaseChanges("## Apple Music pour Android\nAnciennes notes…").isEmpty())
        assertTrue(currentReleaseChanges("").isEmpty())
    }
}
