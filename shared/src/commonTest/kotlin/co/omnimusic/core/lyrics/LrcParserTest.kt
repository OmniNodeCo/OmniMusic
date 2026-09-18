package co.omnimusic.core.lyrics

import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcParserTest {

    fun testParsesCentisecondTimestampsAndClosesLinesWithTheNextStart() {
        val lines = LrcParser.parse(
            """
            [00:30.75] One more time
            [01:04.92] We're gonna celebrate
            """.trimIndent()
        )
        assertEquals(2, lines.size)
        assertEquals(30_750L, lines[0].startMillis)
        assertEquals("One more time", lines[0].text)
        assertEquals(64_920L, lines[0].endMillis)
        assertEquals(34_170L, lines[0].durationMillis)
        // The last line has no successor, so it has no explicit end.
        assertEquals(null, lines[1].endMillis)
    }

    fun testKeepsBlankLinesBecauseTheyMarkInstrumentalGaps() {
        val lines = LrcParser.parse("[00:30.75] One more time\n[00:33.18] \n[00:46.35] One more time")
        assertEquals(3, lines.size)
        assertTrue(lines[1].isBlankLine)
        assertEquals(33_180L, lines[1].startMillis)
    }

    fun testSeveralTimestampsOnOneLineBecomeSeparateLines() {
        val lines = LrcParser.parse("[01:00.00][02:00.00] chorus")
        assertEquals(2, lines.size)
        assertEquals(60_000L, lines[0].startMillis)
        assertEquals(120_000L, lines[1].startMillis)
        assertEquals("chorus", lines[0].text)
        assertEquals("chorus", lines[1].text)
    }

    fun testHandlesMillisecondFractionsAndBareSeconds() {
        assertEquals(1_123L, LrcParser.parse("[00:01.123] x")[0].startMillis)
        assertEquals(1_120L, LrcParser.parse("[00:01.12] x")[0].startMillis)
        assertEquals(1_100L, LrcParser.parse("[00:01.1] x")[0].startMillis)
        assertEquals(61_000L, LrcParser.parse("[01:01] x")[0].startMillis)
    }

    fun testOffsetTagShiftsEveryLine() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00] a\n[00:20.00] b")
        assertEquals(9_500L, lines[0].startMillis)
        assertEquals(19_500L, lines[1].startMillis)
    }

    fun testOffsetNeverProducesNegativeTimestamps() {
        val lines = LrcParser.parse("[offset:+5000]\n[00:01.00] a")
        assertEquals(0L, lines[0].startMillis)
    }

    fun testIgnoresMetadataTags() {
        val lines = LrcParser.parse(
            """
            [ti:One More Time]
            [ar:Daft Punk]
            [al:Discovery]
            [by:transcriber]
            [00:01.00] first line
            """.trimIndent()
        )
        assertEquals(1, lines.size)
        assertEquals("first line", lines[0].text)
    }

    fun testSortsLinesThatArriveOutOfOrder() {
        val lines = LrcParser.parse("[00:20.00] second\n[00:10.00] first")
        assertEquals(listOf("first", "second"), lines.map { it.text })
    }

    fun testSkipsUntimestampedProse() {
        val lines = LrcParser.parse("[00:01.00] timed\nthis line has no timestamp\n[00:02.00] also timed")
        assertEquals(2, lines.size)
    }

    fun testHandlesCrlfLineEndings() {
        val lines = LrcParser.parse("[00:01.00] a\r\n[00:02.00] b\r\n")
        assertEquals(2, lines.size)
        assertEquals("b", lines[1].text)
    }

    fun testEmptyInputYieldsNoLines() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("[ti:only metadata]").isEmpty())
    }

    fun testLineIndexAtWalksTheTranscript() {
        val lyrics = Lyrics(
            trackName = "t",
            artistName = "a",
            lines = listOf(
                LyricLine(0, 1000, "intro"),
                LyricLine(1000, 2000, "verse"),
                LyricLine(2000, null, "chorus"),
            ),
        )
        assertEquals(-1, lyrics.lineIndexAt(-1), "before the first line there is nothing on screen")
        assertEquals(0, lyrics.lineIndexAt(0))
        assertEquals(0, lyrics.lineIndexAt(999))
        assertEquals(1, lyrics.lineIndexAt(1000))
        assertEquals(2, lyrics.lineIndexAt(5_000))
        assertEquals("chorus", lyrics.lineAt(5_000)?.text)
        assertEquals(null, lyrics.lineAt(-5))
        assertTrue(lyrics.isSynced)
    }

    fun testPlainOnlyLyricsFallBackToPlainLines() {
        val lyrics = Lyrics(trackName = "t", artistName = "a", plainText = "line one\n\nline two\n")
        assertTrue(!lyrics.isSynced)
        assertEquals(listOf("line one", "line two"), lyrics.plainLines)
        assertEquals(-1, lyrics.lineIndexAt(1_000))
    }
}
