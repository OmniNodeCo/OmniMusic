package co.omnimusic.core.util

/** `226` -> `3:46`, `3725` -> `1:02:05`. */
fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

/** Percent-encodes everything outside the RFC 3986 unreserved set, so queries survive `artist: x&y`. */
fun urlEncode(value: String): String {
    val out = StringBuilder()
    val bytes = value.encodeToByteArray()
    for (b in bytes) {
        val c = b.toInt().toChar()
        val unreserved = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~'
        if (unreserved) {
            out.append(c)
        } else {
            val hex = (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
            out.append('%').append(hex)
        }
    }
    return out.toString()
}

fun buildUrl(base: String, params: Map<String, String>): String {
    if (params.isEmpty()) return base
    val query = params.entries.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }
    return if (base.contains('?')) "$base&$query" else "$base?$query"
}

/** Case/diacritic/punctuation-insensitive normalization used by both local search and highlighting. */
fun normalizeForSearch(value: String): String {
    val builder = StringBuilder(value.length)
    for (ch in value) {
        when {
            ch.isLetterOrDigit() -> builder.append(ch.lowercaseChar())
            else -> builder.append(' ')
        }
    }
    return builder.toString().replace(Regex("\\s+"), " ").trim()
}

fun String?.orPlaceholder(placeholder: String): String =
    if (this.isNullOrBlank()) placeholder else this
