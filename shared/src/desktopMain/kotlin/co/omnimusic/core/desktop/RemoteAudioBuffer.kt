package co.omnimusic.core.desktop

import co.omnimusic.core.audio.AudioSourceException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Reads remote audio into memory so that it can be seeked.
 *
 * `AudioSystem.getAudioInputStream(URL)` wraps the connection's own stream, and that stream does
 * not support `mark`. Java Sound's decoders can only rewind inside bytes they have marked, so
 * `AudioInputStream.reset()` — the only way [JavaSoundAudioOutput] seeks — throws, and every seek of
 * a remote track surfaced as "cannot seek this stream": the arrow keys, the seek bar, resuming a
 * saved position, and looping a track with repeat-one.
 *
 * Buffering removes the problem rather than working around it, because a
 * [java.io.ByteArrayInputStream] always supports `mark`. Music API previews are around 30 seconds of
 * MP3 — a few hundred kilobytes — so the whole body fits comfortably inside [DEFAULT_LIMIT_BYTES],
 * and the limit is what keeps an unexpected multi-gigabyte response from being buffered anyway.
 */
object RemoteAudioBuffer {

    /** 64 MB: far above any preview, and a hard stop on a response that is not audio at all. */
    const val DEFAULT_LIMIT_BYTES: Int = 64 * 1024 * 1024

    private const val CHUNK_BYTES = 16 * 1024
    private const val CONNECT_TIMEOUT_MILLIS = 10_000
    private const val READ_TIMEOUT_MILLIS = 20_000

    /**
     * Opens [url] as an audio stream that can be rewound.
     *
     * This is the fix, in one line: decode from memory rather than from the connection, so the mark
     * a seek returns to always exists. Handing `AudioSystem` the URL directly is what broke seeking.
     */
    fun open(url: URL, limitBytes: Int = DEFAULT_LIMIT_BYTES): AudioInputStream =
        AudioSystem.getAudioInputStream(ByteArrayInputStream(read(url, limitBytes)))

    /** The raw body behind [url], which is what a seek re-decodes from. */
    fun bytes(url: URL, limitBytes: Int = DEFAULT_LIMIT_BYTES): ByteArray = read(url, limitBytes)

    /** Reads at most [limitBytes] of [url], failing with a readable error rather than buffering forever. */
    fun read(url: URL, limitBytes: Int = DEFAULT_LIMIT_BYTES): ByteArray {
        val connection = url.openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        if (connection is HttpURLConnection) {
            val status = connection.responseCode
            // An expired signed link comes back as 403. Saying so beats quoting the URL: a preview
            // link is 300 characters of token and tells the user nothing.
            if (status !in 200..299) throw AudioSourceException(refusalMessage(status))
        }
        connection.getInputStream().use { input ->
            return readAtMost(input, limitBytes, url.toString().substringBefore('?'))
        }
    }

    private fun refusalMessage(status: Int): String = when (status) {
        403, 410 -> "This track's stream link has expired (HTTP $status). Music services sign " +
            "preview links for only a few minutes, so play the track again to get a fresh one."
        404 -> "The music service no longer has this stream (HTTP 404)."
        else -> "The music service refused this stream (HTTP $status)."
    }

    private fun readAtMost(input: InputStream, limitBytes: Int, description: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(CHUNK_BYTES)
        var total = 0
        while (true) {
            val read = input.read(buffer, 0, buffer.size)
            if (read <= 0) break
            total += read
            if (total > limitBytes) {
                throw AudioSourceException(
                    "$description is larger than the ${limitBytes / (1024 * 1024)} MB this player " +
                        "buffers; seeking needs the whole body in memory",
                )
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
