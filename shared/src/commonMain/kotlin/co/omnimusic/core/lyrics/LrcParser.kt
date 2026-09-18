package co.omnimusic.core.lyrics

/**
 * An Enhanced-LRC parser.
 *
 * Handles what real-world `.lrc` files actually contain: centisecond *and* millisecond fractions,
 * several timestamps sharing one line, metadata tags (`[ar:]`, `[ti:]`, …), the `[offset:]` shift,
 * and the empty lines services such as LRCLIB emit to represent instrumental gaps.
 *
 * End times are not part of the LRC format, so each line is closed off by the start of the next
 * one. Sources that publish explicit ends (LRCLIB's `lyricsfile`) can build [LyricLine] directly.
 */
object LrcParser {

    /** `[ar:Artist]`, `[offset:+500]`, … — a bracket whose tag is not a time. */
    private val METADATA_TAG = Regex("^\\[([A-Za-z#]+):(.*)]$")

    /** `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss:xx]`. */
    private val TIMESTAMP = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(input: String): List<LyricLine> {
        var offsetMillis = 0L
        val parsed = ArrayList<LyricLine>()

        for (rawLine in input.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) continue

            METADATA_TAG.find(line.trim())?.let { tag ->
                if (tag.groupValues[1].equals("offset", ignoreCase = true)) {
                    tag.groupValues[2].trim().toLongOrNull()?.let { offsetMillis = it }
                }
                return@let // metadata line carries no lyric text
            }

            val stamps = TIMESTAMP.findAll(line).toList()
            if (stamps.isEmpty()) continue // untimestamped prose inside a synced file
            val text = line.substring(stamps.last().range.last + 1).trim()

            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toLongOrNull() ?: continue
                val seconds = stamp.groupValues[2].toLongOrNull() ?: 0L
                val start = minutes * 60_000L + seconds * 1000L + fractionMillis(stamp.groupValues[3]) - offsetMillis
                parsed += LyricLine(startMillis = start.coerceAtLeast(0L), text = text)
            }
        }

        parsed.sortBy { it.startMillis }
        return parsed.mapIndexed { index, line ->
            line.copy(endMillis = parsed.getOrNull(index + 1)?.startMillis)
        }
    }

    /** LRC fractions are centiseconds by convention, but files with three digits mean milliseconds. */
    private fun fractionMillis(raw: String): Long = when (raw.length) {
        0 -> 0L
        1 -> (raw.toLongOrNull() ?: 0L) * 100L
        2 -> (raw.toLongOrNull() ?: 0L) * 10L
        else -> raw.take(3).toLongOrNull() ?: 0L
    }
}
