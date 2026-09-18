package co.omnimusic.core.desktop

import co.omnimusic.core.json.get
import co.omnimusic.core.json.JsonParser
import co.omnimusic.core.json.JsonWriter
import co.omnimusic.core.json.asObjectOrNull
import co.omnimusic.core.json.asStringOrNull
import co.omnimusic.core.json.Json
import co.omnimusic.core.json.jsonNumber
import co.omnimusic.core.json.jsonObject
import co.omnimusic.core.json.jsonString
import co.omnimusic.core.store.KeyValueStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Desktop [KeyValueStore]: a single JSON document in the user's config directory.
 *
 * Writes go to a temp file and are then moved into place, so a crash mid-write cannot leave a
 * half-written store behind and wipe the user's playlists.
 */
class JvmKeyValueStore(private val file: Path) : KeyValueStore {

    private val values: MutableMap<String, String> = LinkedHashMap()
    private var loaded = false

    @Synchronized
    override fun getString(key: String): String? {
        ensureLoaded()
        return values[key]
    }

    @Synchronized
    override fun putString(key: String, value: String) {
        ensureLoaded()
        values[key] = value
        flush()
    }

    @Synchronized
    override fun remove(key: String) {
        ensureLoaded()
        if (values.remove(key) != null) flush()
    }

    @Synchronized
    override fun keys(prefix: String): Set<String> {
        ensureLoaded()
        return values.keys.filter { it.startsWith(prefix) }.toSet()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!Files.exists(file)) return
        val raw = runCatching { Files.readString(file) }.getOrNull() ?: return
        val root = JsonParser.parseOrNull(raw).asObjectOrNull() ?: return
        val entries = root["entries"].asObjectOrNull() ?: return
        for ((key, value) in entries.fields) {
            value.asStringOrNull()?.let { values[key] = it }
        }
    }

    private fun flush() {
        file.parent?.let { Files.createDirectories(it) }
        val encoded = JsonWriter.write(
            jsonObject(
                "schema" to jsonNumber(1),
                "entries" to Json.Obj(values.mapValues { (_, v) -> jsonString(v) }),
            )
        )
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, encoded)
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {

        /** `%APPDATA%\OmniMusic` on Windows, `~/.config/OmniMusic` on Linux, `~/Library/...` on macOS. */
        fun default(appName: String = "OmniMusic"): JvmKeyValueStore {
            val home = System.getProperty("user.home")
            val os = System.getProperty("os.name").lowercase()
            val directory = when {
                os.contains("win") -> System.getenv("APPDATA")?.let { Path.of(it) } ?: Path.of(home, "AppData", "Roaming")
                os.contains("mac") || os.contains("darwin") -> Path.of(home, "Library", "Application Support")
                else -> System.getenv("XDG_CONFIG_HOME")?.let { Path.of(it) } ?: Path.of(home, ".config")
            }.resolve(appName)
            return JvmKeyValueStore(directory.resolve("store.json"))
        }
    }
}
