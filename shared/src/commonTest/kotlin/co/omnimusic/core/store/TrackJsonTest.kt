package co.omnimusic.core.store

import co.omnimusic.core.json.JsonParser
import co.omnimusic.core.json.JsonWriter
import co.omnimusic.core.json.asObjectOrNull
import co.omnimusic.core.json.get
import co.omnimusic.core.model.Album
import co.omnimusic.core.model.Artist
import co.omnimusic.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The on-disk encoding of a track, which every playlist, history entry and saved session passes
 * through.
 *
 * The point of the round-trip tests is that they compare whole objects rather than spot-checking
 * fields: a codec that quietly drops a field is invisible until something starts reading it, and by
 * then the user's saved library has already been rewritten without it.
 */
class TrackJsonTest {

    private val fullTrack = Track(
        id = "3135553",
        title = "One More Time",
        artist = Artist(id = "27", name = "Daft Punk", imageUrl = "https://cdn.test/artist/27.jpg"),
        album = Album(
            id = "302127",
            title = "Discovery",
            artistName = "Daft Punk",
            imageUrl = "https://cdn.test/album/302127.jpg",
            trackCount = 14,
            releaseDate = "2001-03-07",
        ),
        durationSeconds = 320,
        streamUrl = "https://cdn.test/preview/3135553.mp3",
        explicit = false,
        popularity = 938_117L,
    )

    fun testRoundTripsEveryFieldOfAFullyPopulatedTrack() {
        val decoded = TrackJson.decode(TrackJson.encode(fullTrack))
        assertEquals(fullTrack, decoded)
    }

    fun testRoundTripsThroughTheWrittenJsonText() {
        // Not just object to object: through the serialised form, which is what actually lands on
        // disk and what a future version of the app will read back.
        val text = JsonWriter.write(TrackJson.encode(fullTrack))
        val decoded = TrackJson.decode(JsonParser.parseOrNull(text))
        assertEquals(fullTrack, decoded)
    }

    fun testRoundTripsATrackWithNoAlbum() {
        val bare = Track(
            id = "1",
            title = "Standalone",
            artist = Artist(id = "a1", name = "Someone"),
            durationSeconds = 90,
        )
        val decoded = assertNotNull(TrackJson.decode(TrackJson.encode(bare)))
        assertEquals(bare, decoded)
        assertNull(decoded.album)
    }

    fun testRoundTripsAnExplicitTrackWithNoStreamUrl() {
        val metadataOnly = Track(
            id = "2",
            title = "Unplayable",
            artist = Artist(id = "a2", name = "Someone Else"),
            durationSeconds = 200,
            streamUrl = null,
            explicit = true,
        )
        val decoded = assertNotNull(TrackJson.decode(TrackJson.encode(metadataOnly)))
        assertEquals(metadataOnly, decoded)
        assertNull(decoded.streamUrl)
        assertTrue(decoded.explicit)
    }

    fun testMissingFieldsFallBackToDefaults() {
        val decoded = assertNotNull(TrackJson.decode(JsonParser.parseOrNull("""{"id":"x"}""")))
        assertEquals("x", decoded.id)
        assertEquals("Unknown title", decoded.title)
        assertEquals("Unknown artist", decoded.artistName)
        assertEquals(0, decoded.durationSeconds)
        assertEquals(false, decoded.explicit)
        assertEquals(0L, decoded.popularity)
        assertNull(decoded.album)
    }

    fun testUnknownFieldsAreIgnored() {
        val decoded = assertNotNull(
            TrackJson.decode(JsonParser.parseOrNull("""{"id":"x","title":"T","fromTheFuture":{}}""")),
        )
        assertEquals("T", decoded.title)
    }

    fun testANodeWithoutAnIdIsNotATrack() {
        assertNull(TrackJson.decode(JsonParser.parseOrNull("""{"title":"no id here"}""")))
    }

    fun testSomethingThatIsNotAnObjectIsNotATrack() {
        assertNull(TrackJson.decode(JsonParser.parseOrNull("42")))
        assertNull(TrackJson.decode(null))
    }

    fun testAnAlbumWithOnlyAnIdStillRoundTrips() {
        val partial = fullTrack.copy(
            album = Album(id = "302127", title = "", artistName = "Daft Punk"),
        )
        val decoded = assertNotNull(TrackJson.decode(TrackJson.encode(partial)))
        assertEquals("302127", decoded.album?.id)
    }

    fun testDecodeAllSkipsEntriesThatAreNotTracks() {
        val node = JsonParser.parseOrNull(
            """[{"id":"a"},{"title":"no id"},"not an object",{"id":"b"}]""",
        )
        val tracks = TrackJson.decodeAll(node)
        assertEquals(listOf("a", "b"), tracks.map { it.id })
    }

    fun testDecodeAllOnSomethingThatIsNotAnArrayIsEmpty() {
        assertEquals(emptyList(), TrackJson.decodeAll(JsonParser.parseOrNull("""{"id":"a"}""")))
        assertEquals(emptyList(), TrackJson.decodeAll(null))
    }

    fun testTheWrittenDocumentCarriesTheDocumentedKeys() {
        val obj = assertNotNull(
            JsonWriter.write(TrackJson.encode(fullTrack))
                .let { JsonParser.parseOrNull(it) }
                .asObjectOrNull(),
        )
        for (key in listOf(
            "id", "title", "duration", "stream", "explicit",
            "artistId", "artist", "artistImage",
            "albumId", "album", "albumImage", "popularity", "albumTracks", "albumReleased",
        )) {
            assertNotNull(obj[key], "encoded track is missing \"$key\"")
        }
    }
}
