package co.omnimusic.core.store

import co.omnimusic.core.testing.RecordingKeyValueStore
import co.omnimusic.core.testing.testTrack
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistStoreTest {

    private val store = RecordingKeyValueStore()

    fun testCreateIsPersistedAndSurvivesAReload() {
        val playlists = PlaylistStore(store, idFactory = { "p1" })
        val created = playlists.create("Road trip")
        assertEquals("p1", created.id)
        assertEquals("Road trip", created.name)

        val reloaded = PlaylistStore(RecordingKeyValueStore(store.rawSnapshot()), idFactory = { "p2" })
        assertEquals(1, reloaded.all().size)
        assertEquals("Road trip", reloaded.byId("p1")?.name)
    }

    fun testBlankNamesAreReplaced() {
        val playlists = PlaylistStore(store, idFactory = { "p1" })
        assertEquals("New playlist", playlists.create("   ").name)
    }

    fun testAddTracksDeduplicatesById() {
        val playlists = PlaylistStore(store, idFactory = { "p1" })
        playlists.create("Mix")
        val track = testTrack("1")
        playlists.addTrack("p1", track)
        val afterDuplicate = playlists.addTracks("p1", listOf(track, testTrack("2")))
        assertEquals(listOf("1", "2"), afterDuplicate!!.tracks.map { it.id })
        assertEquals("6:00", playlists.byId("p1")!!.durationLabel)
    }

    fun testRemoveAndMoveTracks() {
        val playlists = PlaylistStore(store, idFactory = { "p1" })
        playlists.create("Mix")
        playlists.addTracks("p1", listOf(testTrack("1"), testTrack("2"), testTrack("3")))

        val moved = playlists.moveTrack("p1", from = 0, to = 2)
        assertEquals(listOf("2", "3", "1"), moved!!.tracks.map { it.id })

        val removed = playlists.removeTrack("p1", 0)
        assertEquals(listOf("3", "1"), removed!!.tracks.map { it.id })

        // Out-of-range operations are no-ops, not crashes.
        assertEquals(2, playlists.removeTrack("p1", 99)!!.tracks.size)
        assertEquals(2, playlists.moveTrack("p1", 0, 99)!!.tracks.size)
    }

    fun testPlaylistsContainingFindsTrackMembership() {
        val playlists = PlaylistStore(store)
        playlists.create("A")
        playlists.create("B")
        playlists.addTrack(playlists.all()[0].id, testTrack("shared"))
        val matches = playlists.playlistsContaining("shared")
        assertEquals(1, matches.size)
        assertEquals("A", matches[0].name)
        assertTrue(playlists.playlistsContaining("missing").isEmpty())
    }

    fun testRenameAndDelete() {
        val playlists = PlaylistStore(store, idFactory = { "p1" })
        playlists.create("Old")
        assertEquals("New", playlists.rename("p1", "New")!!.name)
        assertTrue(playlists.delete("p1"))
        assertTrue(playlists.all().isEmpty())
        assertFalse(playlists.delete("p1"))
        assertNull(playlists.rename("p1", "x"))
    }

    fun testCorruptEntriesCostOnePlaylistNotTheLibrary() {
        val seeded = mapOf(
            "playlist.good" to PlaylistJson.encode(
                co.omnimusic.core.model.Playlist("good", "Good", listOf(testTrack("1")))
            ),
            "playlist.bad" to "{{{ not json",
            "unrelated.key" to "ignored",
        )
        val playlists = PlaylistStore(RecordingKeyValueStore(seeded))
        assertEquals(1, playlists.all().size)
        assertEquals("Good", playlists.all()[0].name)
        assertEquals(1, playlists.all()[0].tracks.size)
    }

    fun testRoundTripKeepsEveryField() {
        val track = testTrack(
            id = "3135553",
            title = "One More Time",
            artistName = "Daft Punk",
            durationSeconds = 320,
            streamUrl = "https://cdn.test/preview.mp3",
            albumTitle = "Discovery",
        )
        val original = co.omnimusic.core.model.Playlist(
            id = "p1",
            name = "Mix",
            tracks = listOf(track),
            description = "desc",
            imageUrl = "https://cdn.test/cover.jpg",
        )
        val decoded = PlaylistJson.decode(PlaylistJson.encode(original))
        assertNotNull(decoded)
        assertEquals(original.name, decoded.name)
        assertEquals(original.description, decoded.description)
        assertEquals(original.imageUrl, decoded.imageUrl)

        val restored = decoded.tracks[0]
        assertEquals(track.id, restored.id)
        assertEquals(track.title, restored.title)
        assertEquals(track.durationSeconds, restored.durationSeconds)
        assertEquals(track.streamUrl, restored.streamUrl)
        assertEquals(track.artist.name, restored.artist.name)
        assertEquals(track.album?.title, restored.album?.title)
        assertEquals(track.album?.imageUrl, restored.album?.imageUrl)
    }

    fun testDecodeRejectsGarbage() {
        assertNull(PlaylistJson.decode("nope"))
        assertNull(PlaylistJson.decode("""{"name":"missing id"}"""))
    }

    fun testGeneratedIdsAreUsable() {
        val ids = (1..50).map { PlaylistStore.generateId() }
        assertEquals(50, ids.distinct().size)
        assertTrue(ids.all { it.length == 8 })
    }
}
