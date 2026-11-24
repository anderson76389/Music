package fr.makoff.music.viewmodels

import fr.makoff.music.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSpeedDialTest {
    @Test
    fun `home recommendations fill sparse speed dial without replacing pinned items`() {
        val pinned = song("pinned")
        val home = song("home")

        val result =
            buildSpeedDialItems(
                pinned = listOf(pinned),
                keepListening = emptyList(),
                quickPicks = emptyList(),
                home = listOf(pinned.copy(title = "duplicate"), home),
            )

        assertEquals(listOf(pinned, home), result)
    }

    @Test
    fun `expanded speed dial retains up to 54 unique items and mixes sources`() {
        val pinned = song("pinned")
        val recent = (1..36).map { song("recent$it") }
        val quick = (1..48).map { song("quick$it") }
        val result = buildSpeedDialItems(listOf(pinned), recent, quick, quick)
        assertEquals(54, result.size)
        assertEquals(pinned, result.first())
        assertEquals(54, result.distinctBy { it.id }.size)
        assertEquals(12, result.count { it.id.startsWith("recent") })
        assertEquals(41, result.count { it.id.startsWith("quick") })
    }

    private fun song(id: String) =
        SongItem(
            id = id,
            title = id,
            artists = emptyList(),
            thumbnail = "",
        )
}
