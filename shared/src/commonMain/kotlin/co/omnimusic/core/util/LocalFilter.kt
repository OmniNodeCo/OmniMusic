package co.omnimusic.core.util

/**
 * Instant, offline filtering of a list the app already has in memory — the queue, a playlist, a
 * downloaded album.
 *
 * Deliberately not fuzzy-matching: every query token has to appear in the text, in any order, which
 * is what people expect from a filter box and never produces surprising results. Matching is
 * case-insensitive and ignores punctuation via [normalizeForSearch].
 */
object LocalFilter {

    fun matches(text: String, query: String): Boolean {
        val tokens = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val haystack = normalizeForSearch(text)
        return tokens.all { token -> haystack.contains(token) }
    }

    /** Filters [items], matching each token against the concatenation of [selectors]' output. */
    fun <T> filter(items: List<T>, query: String, vararg selectors: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items.filter { item -> matches(selectors.joinToString(" ") { it(item) }, query) }
    }
}
