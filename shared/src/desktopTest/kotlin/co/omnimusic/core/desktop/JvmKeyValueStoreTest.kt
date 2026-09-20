package co.omnimusic.core.desktop

import co.omnimusic.core.json.JsonParser
import co.omnimusic.core.json.asIntOrDefault
import co.omnimusic.core.json.asObjectOrNull
import co.omnimusic.core.json.asStringOrNull
import co.omnimusic.core.json.get
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop persistence layer, against a real temporary directory.
 *
 * This is the class that owns the user's playlists, history and last session on desktop, and it is
 * the one part of the data path that a fake `KeyValueStore` cannot stand in for: the interesting
 * behaviour is the file format, the atomic replace, and what happens when the file on disk is
 * garbage.
 */
class JvmKeyValueStoreTest {

    private val directory = Files.createTempDirectory("omnimusic-store")
    private val file = directory.resolve("store.json")

    fun testRoundTripsThroughTheFile() {
        val store = JvmKeyValueStore(file)
        store.putString("greeting", "hello")
        assertEquals("hello", store.getString("greeting"))

        // A second instance has to read it back from disk, not from the first one's memory.
        assertEquals("hello", JvmKeyValueStore(file).getString("greeting"))
    }

    fun testWritesTheDocumentedShapeOnDisk() {
        JvmKeyValueStore(file).putString("playlists", "[]")
        val root = assertNotNull(JsonParser.parseOrNull(Files.readString(file)).asObjectOrNull())
        assertEquals(1, root["schema"].asIntOrDefault(0))
        val entries = assertNotNull(root["entries"].asObjectOrNull())
        assertEquals("[]", entries["playlists"].asStringOrNull())
    }

    fun testCreatesMissingParentDirectories() {
        val nested = directory.resolve("a/b/c/store.json")
        JvmKeyValueStore(nested).putString("k", "v")
        assertTrue(Files.exists(nested))
        assertEquals("v", JvmKeyValueStore(nested).getString("k"))
    }

    fun testNoTempFileIsLeftBehind() {
        val store = JvmKeyValueStore(file)
        store.putString("a", "1")
        store.putString("a", "2")
        store.remove("a")
        val leftovers = Files.list(directory).use { stream ->
            stream.map { it.fileName.toString() }.toArray().map { it.toString() }
        }
        assertContentEquals(listOf("store.json"), leftovers.sorted())
    }

    fun testRemoveDeletesTheKeyOnDiskToo() {
        val store = JvmKeyValueStore(file)
        store.putString("a", "1")
        store.putString("b", "2")
        store.remove("a")
        assertNull(store.getString("a"))
        assertNull(JvmKeyValueStore(file).getString("a"))
        assertEquals("2", JvmKeyValueStore(file).getString("b"))
    }

    fun testRemoveOfAnUnknownKeyDoesNotRewriteTheFile() {
        val store = JvmKeyValueStore(file)
        store.putString("a", "1")
        val before = Files.getLastModifiedTime(file)
        store.remove("not-there")
        assertEquals(before, Files.getLastModifiedTime(file))
    }

    fun testKeysAreFilteredByPrefix() {
        val store = JvmKeyValueStore(file)
        store.putString("playlist.p1", "x")
        store.putString("playlist.p2", "y")
        store.putString("playback.v1", "z")
        assertEquals(setOf("playlist.p1", "playlist.p2"), store.keys("playlist."))
        assertEquals(setOf<String>(), store.keys("nothing."))
    }

    fun testKeysAndValuesSurviveJsonHostileCharacters() {
        val store = JvmKeyValueStore(file)
        val key = "weird.\"key\".\\x"
        val value = "quotes \" backslash \\ newline \n tab \t unicode é 日本語"
        store.putString(key, value)
        val reopened = JvmKeyValueStore(file)
        assertEquals(value, reopened.getString(key))
        assertTrue(key in reopened.keys("weird."))
    }

    fun testACorruptFileReadsAsEmptyAndRecoversOnTheNextWrite() {
        Files.writeString(file, "{ this is not json")
        val store = JvmKeyValueStore(file)
        assertNull(store.getString("anything"))
        assertEquals(setOf<String>(), store.keys(""))

        // The next write must replace the garbage rather than fail or preserve it.
        store.putString("fresh", "value")
        assertEquals("value", JvmKeyValueStore(file).getString("fresh"))
    }

    fun testEntriesThatAreNotStringsAreIgnored() {
        Files.writeString(file, """{"schema":1,"entries":{"good":"text","bad":42,"worse":null}}""")
        val store = JvmKeyValueStore(file)
        assertEquals("text", store.getString("good"))
        assertNull(store.getString("bad"))
        assertNull(store.getString("worse"))
    }

    fun testAnUnreadableFileIsTreatedAsAnEmptyStore() {
        Files.writeString(file, "")
        assertNull(JvmKeyValueStore(file).getString("anything"))
    }

    fun testAMissingFileIsAnEmptyStore() {
        val absent = directory.resolve("never-written.json")
        assertFalse(Files.exists(absent))
        val store = JvmKeyValueStore(absent)
        assertNull(store.getString("anything"))
        assertEquals(setOf<String>(), store.keys(""))
        assertFalse(Files.exists(absent))
    }

    fun testAskingForTheDefaultDirectoryCreatesNothing() {
        val directory = JvmKeyValueStore.defaultDirectory("NeverWritten")
        assertTrue(directory.endsWith(Path.of("NeverWritten")), "got $directory")
        // The store creates its file on the first write and the startup log creates its own
        // directory; neither should happen as a side effect of asking where they would go.
        assertFalse(Files.exists(directory))
    }

    fun testTheDefaultStoreLivesInsideTheDefaultDirectory() {
        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", directory.toString())
        val expected = JvmKeyValueStore.defaultDirectory("TestApp")
        try {
            JvmKeyValueStore.default("TestApp").putString("k", "v")
            assertTrue(
                Files.exists(expected.resolve("store.json")),
                "expected the default store under $expected, which is where the startup log goes too",
            )
        } finally {
            System.setProperty("user.home", originalHome)
            runCatching { Files.deleteIfExists(expected.resolve("store.json")) }
            runCatching { Files.deleteIfExists(expected) }
        }
    }

    fun testOverwritingAKeyKeepsTheOtherEntries() {
        val store = JvmKeyValueStore(file)
        store.putString("a", "1")
        store.putString("b", "2")
        store.putString("a", "3")
        val reopened = JvmKeyValueStore(file)
        assertEquals("3", reopened.getString("a"))
        assertEquals("2", reopened.getString("b"))
    }
}
