package co.omnimusic.core.desktop

import javax.sound.sampled.AudioFormat

/**
 * What Java Sound needs before it will play a decoded stream.
 *
 * The JDK's own decoders (WAV, AIFF, AU) hand back PCM, which a `SourceDataLine` can consume
 * directly. A third-party SPI — mp3spi for MP3, for instance — hands back a stream in *its*
 * encoding (`MPEG1L3`) and expects the caller to ask for a conversion. Skipping that step is the
 * usual reason "I added the MP3 library and it still fails": the line is opened against an
 * encoding no mixer implements, and Java Sound reports it as a missing audio device rather than a
 * missing decoder.
 *
 * Kept as pure functions over [AudioFormat] so the decision is testable without an audio device.
 */
object PcmConversion {

    /** True when the encoding can go straight to an output line. */
    fun isPcm(format: AudioFormat): Boolean = when (format.encoding) {
        AudioFormat.Encoding.PCM_SIGNED,
        AudioFormat.Encoding.PCM_UNSIGNED,
        AudioFormat.Encoding.PCM_FLOAT,
        -> true

        // Some SPIs invent their own PCM-flavoured encoding names ("PCM_FLOAT_LE", …).
        else -> format.encoding.toString().startsWith("PCM", ignoreCase = true)
    }

    fun needsConversion(format: AudioFormat): Boolean = !isPcm(format)

    /**
     * The PCM format to request from the converter, or null when the source does not describe
     * itself well enough to build one. Decoders occasionally report `NOT_SPECIFIED` (-1) for the
     * sample rate or channel count until the first frame is read; there is nothing sensible to ask
     * for in that case, and the caller has to say so instead of opening a line at -1 Hz.
     */
    fun targetFor(format: AudioFormat): AudioFormat? {
        val rate = format.sampleRate
        val channels = format.channels
        if (rate <= 0f || channels <= 0) return null
        return AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            rate,
            16,
            channels,
            channels * 2,
            rate,
            false,
        )
    }
}
