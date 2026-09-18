package co.omnimusic.core.audio

/**
 * Where sound actually comes out. Everything above this interface is platform agnostic; each
 * target implements it (Java Sound on desktop, `MediaPlayer`/`ExoPlayer` on Android).
 *
 * Implementations are only ever driven by [co.omnimusic.core.player.PlaybackEngine] from a single
 * thread, so they do not need to be thread-safe.
 */
interface AudioOutput {

    /** Loads [source] and seeks to the start. Must leave the output paused, not playing. */
    fun prepare(source: AudioSource)

    fun play()

    fun pause()

    /** Stops and releases the current source. */
    fun stop()

    fun seekTo(positionMillis: Long)

    /**
     * Current playback position, or `-1` when the implementation cannot report one. The engine
     * falls back to accumulating the ticks it receives, which keeps it testable.
     */
    fun positionMillis(): Long = -1L

    fun setVolume(volume: Float)

    /** True when a source is loaded and ready to play. */
    fun isReady(): Boolean
}

/** Thrown when a source cannot be decoded — an MP3 on a JDK with no MP3 codec, for instance. */
class UnsupportedAudioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when a source cannot be reached or read at all. */
class AudioSourceException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface AudioSource {

    /** A URL the platform layer streams from (the preview clips a music API hands back). */
    data class Remote(val url: String) : AudioSource

    /**
     * Already-decoded PCM, used for local files and tests. [samples] holds interleaved little-endian
     * integer samples of [PcmFormat.bitsPerSample] width, starting at [offset] for [length] bytes.
     */
    class Pcm(
        val format: PcmFormat,
        val samples: ByteArray,
        val offset: Int = 0,
        val length: Int = samples.size,
    ) : AudioSource {
        override fun equals(other: Any?): Boolean =
            other is Pcm && format == other.format && offset == other.offset &&
                length == other.length && samples.contentEquals(other.samples)

        override fun hashCode(): Int = 31 * format.hashCode() + samples.contentHashCode()

        override fun toString(): String = "Pcm($format, ${length / format.frameSize} frames)"
    }
}

data class PcmFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(channels > 0) { "channels must be positive, was $channels" }
        require(bitsPerSample % 8 == 0 && bitsPerSample in 8..32) { "unsupported bitsPerSample $bitsPerSample" }
    }

    val bytesPerSample: Int get() = bitsPerSample / 8
    val frameSize: Int get() = bytesPerSample * channels

    fun framesFor(millis: Long): Long = sampleRate.toLong() * millis / 1000L

    fun millisFor(frames: Long): Long = if (sampleRate == 0) 0 else frames * 1000L / sampleRate

    fun byteOffsetOfFrame(frame: Long): Int = (frame * frameSize).toInt()
}
