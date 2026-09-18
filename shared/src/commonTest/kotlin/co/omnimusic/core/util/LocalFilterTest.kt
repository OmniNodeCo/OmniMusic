package co.omnimusic.core.util

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFilterTest {

    fun testMatchingIgnoresCaseAndPunctuation() {
        assertTrue(LocalFilter.matches("Harder, Better, Faster, Stronger", "harder faster"))
        assertTrue(LocalFilter.matches("Rap/Hip Hop", "hip hop"))
        assertTrue(LocalFilter.matches("Instant Crush (feat. Julian Casablancas)", "julian"))
    }

    fun testEveryTokenHasToAppearInAnyOrder() {
        assertTrue(LocalFilter.matches("One More Time", "time one"))
        assertFalse(LocalFilter.matches("One More Time", "one never"))
    }

    fun testAnEmptyQueryMatchesEverything() {
        assertTrue(LocalFilter.matches("anything", ""))
        assertTrue(LocalFilter.matches("anything", "   "))
    }

    fun testFilterUsesEverySelector() {
        data class Row(val title: String, val artist: String)
        val rows = listOf(
            Row("One More Time", "Daft Punk"),
            Row("Starboy", "The Weeknd"),
        )
        assertEquals(
            listOf("Starboy"),
            LocalFilter.filter(rows, "weeknd", { it.title }, { it.artist }).map { it.title },
        )
        assertEquals(
            listOf("One More Time"),
            LocalFilter.filter(rows, "daft", { it.title }, { it.artist }).map { it.title },
        )
        assertEquals(2, LocalFilter.filter(rows, "", { it.title }, { it.artist }).size)
    }
}
