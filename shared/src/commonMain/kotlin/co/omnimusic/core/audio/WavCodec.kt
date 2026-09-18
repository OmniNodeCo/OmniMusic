package co.omnimusic.core.audio

/**
 * A dependency-free RIFF/WAVE reader (and, for tests and the demo, writer).
 *
 * It exists for two reasons: the core must be able to inspect and play local PCM audio without a
 * third-party library, and the decode path has to be verifiable on a machine with no sound card —
 * which is exactly what the unit tests in `shared/src/commonTest` do.
 *
 * Supported: 8/16/24/32-bit integer PCM and 32-bit IEEE float, any sample rate, 1..2 channels.
 * Compressed WAVE formats (ADPCM, µ-law, MPEG) are rejected with [UnsupportedAudioException].
 */
object WavCodec {

    const val RIFF_TAG = 0x46464952 // "RIFF" little-endian
    const val WAVE_TAG = 0x45564157 // "WAVE"
    private const val FMT_TAG = 0x20746D66 // "fmt "
    private const val DATA_TAG = 0x61746164 // "data"

    private const val FORMAT_PCM = 1
    private const val FORMAT_IEEE_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    data class Wav(
        val format: PcmFormat,
        val samples: ByteArray,
        val floatEncoded: Boolean = false,
    ) {
        val frameCount: Long get() = samples.size.toLong() / format.frameSize
        val durationMillis: Long get() = format.millisFor(frameCount)

        /** Frame index for a position in milliseconds, clamped into the file. */
        fun frameAt(positionMillis: Long): Long =
            (format.framesFor(positionMillis)).coerceIn(0L, frameCount)

        /** Byte offset of a frame, suitable for `AudioSource.Pcm.offset`. */
        fun byteOffsetOf(positionMillis: Long): Int = format.byteOffsetOfFrame(frameAt(positionMillis))

        /**
         * Decodes up to [maxFrames] frames starting at [fromFrame] into 16-bit signed samples,
         * regardless of the file's native width. Mono output is produced by averaging channels.
         */
        fun readAs16BitMono(fromFrame: Long = 0, maxFrames: Int = Int.MAX_VALUE): ShortArray {
            val available = (frameCount - fromFrame).coerceAtLeast(0)
            val count = minOf(available, maxFrames.toLong()).toInt()
            val out = ShortArray(count)
            val bytesPerSample = format.bytesPerSample
            val stride = format.frameSize
            for (i in 0 until count) {
                val base = ((fromFrame + i) * stride).toInt()
                var sum = 0
                for (channel in 0 until format.channels) {
                    sum += sampleAt(base + channel * bytesPerSample)
                }
                out[i] = (sum / format.channels).toShort()
            }
            return out
        }

        private fun sampleAt(byteIndex: Int): Int {
            if (byteIndex + format.bytesPerSample > samples.size) return 0
            return if (floatEncoded) {
                val bits = littleEndianInt(byteIndex, 4)
                (Float.fromBits(bits) * Short.MAX_VALUE).toInt()
            } else {
                when (format.bitsPerSample) {
                    8 -> ((samples[byteIndex].toInt() and 0xFF) - 128) shl 8
                    16 -> littleEndianInt(byteIndex, 2).toShort().toInt()
                    24 -> {
                        val raw = littleEndianInt(byteIndex, 3)
                        // Sign-extend the 24-bit sample, then drop the low byte.
                        val signed = if (raw and 0x800000 != 0) raw or -0x1000000 else raw
                        signed shr 8
                    }

                    else -> littleEndianInt(byteIndex, 4) shr 16
                }
            }
        }

        private fun littleEndianInt(byteIndex: Int, width: Int): Int {
            var value = 0
            for (i in 0 until width) {
                value = value or ((samples[byteIndex + i].toInt() and 0xFF) shl (8 * i))
            }
            return value
        }

        override fun equals(other: Any?): Boolean =
            other is Wav && format == other.format && floatEncoded == other.floatEncoded &&
                samples.contentEquals(other.samples)

        override fun hashCode(): Int = format.hashCode() * 31 + samples.contentHashCode()
    }

    fun decode(bytes: ByteArray): Wav {
        if (bytes.size < 12) throw UnsupportedAudioException("File is too small to be a RIFF container (${bytes.size} bytes)")
        if (littleEndian(bytes, 0, 4) != RIFF_TAG) throw UnsupportedAudioException("Not a RIFF file")
        if (littleEndian(bytes, 8, 4) != WAVE_TAG) throw UnsupportedAudioException("Not a WAVE file")

        var format: PcmFormat? = null
        var floatEncoded = false
        var data: ByteArray? = null

        var cursor = 12
        while (cursor + 8 <= bytes.size) {
            val tag = littleEndian(bytes, cursor, 4)
            val size = littleEndian(bytes, cursor + 4, 4)
            val bodyStart = cursor + 8
            if (size < 0 || bodyStart > bytes.size) break
            val bodyEnd = minOf(bodyStart + size, bytes.size)
            when (tag) {
                FMT_TAG -> {
                    if (bodyEnd - bodyStart < 16) throw UnsupportedAudioException("Truncated fmt chunk")
                    val audioFormat = littleEndian(bytes, bodyStart, 2)
                    val channels = littleEndian(bytes, bodyStart + 2, 2)
                    val sampleRate = littleEndian(bytes, bodyStart + 4, 4)
                    val bitsPerSample = littleEndian(bytes, bodyStart + 14, 2)
                    var effective = audioFormat
                    if (audioFormat == FORMAT_EXTENSIBLE && bodyEnd - bodyStart >= 40) {
                        // The real codec lives in the sub-format GUID's first two bytes.
                        effective = littleEndian(bytes, bodyStart + 24, 2)
                    }
                    when (effective) {
                        FORMAT_PCM -> floatEncoded = false
                        FORMAT_IEEE_FLOAT -> floatEncoded = true
                        else -> throw UnsupportedAudioException("Unsupported WAVE codec tag $audioFormat")
                    }
                    if (channels == 0) throw UnsupportedAudioException("WAVE header declares zero channels")
                    format = PcmFormat(sampleRate = sampleRate, channels = channels, bitsPerSample = bitsPerSample)
                }

                DATA_TAG -> {
                    data = bytes.copyOfRange(bodyStart, bodyEnd)
                }
            }
            if (data != null && format != null) break
            // Chunks are word aligned.
            cursor = bodyEnd + (size and 1)
        }

        val resolvedFormat = format ?: throw UnsupportedAudioException("WAVE file has no fmt chunk")
        val resolvedData = data ?: throw UnsupportedAudioException("WAVE file has no data chunk")
        return Wav(resolvedFormat, resolvedData, floatEncoded)
    }

    /** Encodes 16-bit signed mono or stereo samples as a canonical PCM WAVE file. */
    fun encode(samples: ShortArray, sampleRate: Int, channels: Int = 1): ByteArray {
        require(channels in 1..2) { "encode supports mono or stereo, got $channels channels" }
        val byteRate = sampleRate * channels * 2
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        writeTag(out, 0, RIFF_TAG)
        writeLittleEndian(out, 4, 36 + dataSize)
        writeTag(out, 8, WAVE_TAG)
        writeTag(out, 12, FMT_TAG)
        writeLittleEndian(out, 16, 16) // fmt chunk size
        writeLittleEndian(out, 20, FORMAT_PCM)
        writeLittleEndian(out, 22, channels)
        writeLittleEndian(out, 24, sampleRate)
        writeLittleEndian(out, 28, byteRate)
        writeLittleEndian(out, 32, channels * 2)
        writeLittleEndian(out, 34, 16)
        writeTag(out, 36, DATA_TAG)
        writeLittleEndian(out, 40, dataSize)
        var cursor = 44
        for (sample in samples) {
            out[cursor] = (sample.toInt() and 0xFF).toByte()
            out[cursor + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            cursor += 2
        }
        return out
    }

    private fun littleEndian(bytes: ByteArray, offset: Int, width: Int): Int {
        var value = 0
        for (i in 0 until width) {
            value = value or ((bytes[offset + i].toInt() and 0xFF) shl (8 * i))
        }
        return value
    }

    private fun writeLittleEndian(bytes: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) {
            bytes[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
    }

    private fun writeTag(bytes: ByteArray, offset: Int, tag: Int) = writeLittleEndian(bytes, offset, tag)
}
