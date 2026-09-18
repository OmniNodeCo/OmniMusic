package co.omnimusic.core.store

/**
 * Key/value persistence. Android backs it with `SharedPreferences`, desktop with a properties file
 * in the user config directory. Only strings go through here; structured data is JSON-encoded by
 * the layer above.
 */
interface KeyValueStore {

    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun remove(key: String)

    /** Keys currently stored that start with [prefix] — used to enumerate playlists. */
    fun keys(prefix: String = ""): Set<String>
}

/** Throws nothing, forgets everything. Handy in previews, tests and first-run fallbacks. */
class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {

    private val values = LinkedHashMap(initial)

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun keys(prefix: String): Set<String> =
        values.keys.filter { it.startsWith(prefix) }.toSet()
}
