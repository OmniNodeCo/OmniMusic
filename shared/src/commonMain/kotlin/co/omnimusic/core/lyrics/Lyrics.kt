package co.omnimusic.core.lyrics

/**
 * Timed lyrics, in the shape a player actually needs them.
 *
 * [lines] is the synced transcript and [plainText] the unsynced fallback; a track may have either,
 * both, or (for an instrumental) neither.
 */
data class Lyrics(
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val durationMillis: Long = 0,
    val instrumental: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val plainText: String? = null,
) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    val plainLines: List<String>
        get() = plainText?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /**
     * Index of the line on screen at [positionMillis], or `-1` before the first line starts
     * (the intro). Binary search, so a 200-line transcript costs nothing per frame.
     */
    fun lineIndexAt(positionMillis: Long): Int {
        if (lines.isEmpty() || positionMillis < lines.first().startMillis) return -1
        var low = 0
        var high = lines.size - 1
        var found = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMillis <= positionMillis) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    fun lineAt(positionMillis: Long): LyricLine? = lineIndexAt(positionMillis).takeIf { it >= 0 }?.let(lines::get)
}

/**
 * One lyric line. [endMillis] is exclusive and may be null when the source only gives start times,
 * in which case the line simply runs until the next one begins.
 */
data class LyricLine(
    val startMillis: Long,
    val endMillis: Long? = null,
    val text: String,
) {
    val isBlankLine: Boolean get() = text.isBlank()
    val durationMillis: Long? get() = endMillis?.minus(startMillis)
}
