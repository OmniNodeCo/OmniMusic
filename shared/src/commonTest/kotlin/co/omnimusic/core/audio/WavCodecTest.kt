package co.omnimusic.core.audio

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WavCodecTest {

    fun testEncodeDecodeRoundTrip() {
        val samples = ShortArray(400) { (it * 97 - 20000).toShort() }
        val bytes = WavCodec.encode(samples, sampleRate = 8000, channels = 1)
        assertEquals(44 + samples.size * 2, bytes.size)

        val wav = WavCodec.decode(bytes)
        assertEquals(PcmFormat(sampleRate = 8000, channels = 1, bitsPerSample = 16), wav.format)
        assertEquals(400L, wav.frameCount)
        assertEquals(50L, wav.durationMillis)
        assertContentEquals(samples, wav.readAs16BitMono())
    }

    fun testPositionMathMapsMillisToFramesAndBytes() {
        val wav = WavCodec.decode(WavCodec.encode(ShortArray(16000), sampleRate = 8000, channels = 1))
        assertEquals(2000L, wav.durationMillis)
        assertEquals(4000L, wav.frameAt(500))
        assertEquals(8000, wav.byteOffsetOf(500))
        // Past the end of the file clamps instead of overflowing.
        assertEquals(16000L, wav.frameAt(999_999))
        assertEquals(0L, wav.frameAt(-10))
    }

    fun testStereoIsAveragedDownToMono() {
        val interleaved = shortArrayOf(1000, -2000, 500, 500)
        val wav = WavCodec.decode(WavCodec.encode(interleaved, sampleRate = 8000, channels = 2))
        assertEquals(2L, wav.frameCount)
        assertContentEquals(shortArrayOf(-500, 500), wav.readAs16BitMono())
    }

    fun testReadsASliceWithoutDecodingTheWholeFile() {
        val wav = WavCodec.decode(WavCodec.encode(ShortArray(100) { it.toShort() }, 8000, 1))
        assertContentEquals(shortArrayOf(10, 11, 12), wav.readAs16BitMono(fromFrame = 10, maxFrames = 3))
    }

    fun testDecodes24BitPcmWithSignExtension() {
        val data = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0x7F, // 0x7FFFFF  -> +32767
            0x00, 0x00, 0x80.toByte(),          // 0x800000  -> -32768
        )
        val wav = WavCodec.decode(wavContainer(audioFormat = 1, channels = 1, sampleRate = 44100, bits = 24, data = data))
        assertContentEquals(shortArrayOf(32767, -32768), wav.readAs16BitMono())
    }

    fun testDecodes32BitFloatPcm() {
        val bytes = ByteArray(8)
        writeLittleEndian(bytes, 0, 1.0f.toBits())
        writeLittleEndian(bytes, 4, (-0.5f).toBits())
        val wav = WavCodec.decode(
            wavContainer(audioFormat = 3, channels = 1, sampleRate = 44100, bits = 32, data = bytes)
        )
        assertTrue(wav.floatEncoded)
        val mono = wav.readAs16BitMono()
        assertEquals(32767, mono[0])
        assertEquals(-16383, mono[1])
    }

    fun testRejectsFilesThatAreNotRiffOrNotWave() {
        assertFailsWith<UnsupportedAudioException> { WavCodec.decode(ByteArray(64)) }
        val notWave = ByteArray(64)
        writeLittleEndian(notWave, 0, WavCodec.RIFF_TAG)
        writeLittleEndian(notWave, 8, 0x41564920) // "AVI "
        assertFailsWith<UnsupportedAudioException> { WavCodec.decode(notWave) }
    }

    fun testRejectsCompressedWaveCodecs() {
        val adpcm = wavContainer(audioFormat = 2, channels = 1, sampleRate = 8000, bits = 16, data = ByteArray(64))
        val error = assertFailsWith<UnsupportedAudioException> { WavCodec.decode(adpcm) }
        assertTrue(error.message!!.contains("codec tag 2"))
    }

    fun testRejectsFilesMissingADataOrFmtChunk() {
        val noData = wavContainer(audioFormat = 1, channels = 1, sampleRate = 8000, bits = 16, data = ByteArray(0))
            .let { bytes -> bytes.copyOfRange(0, 36) } // header + fmt only
        assertFailsWith<UnsupportedAudioException> { WavCodec.decode(noData) }
    }

    fun testPcmFormatRejectsNonsense() {
        assertFailsWith<IllegalArgumentException> { PcmFormat(0, 1, 16) }
        assertFailsWith<IllegalArgumentException> { PcmFormat(44100, 0, 16) }
        assertFailsWith<IllegalArgumentException> { PcmFormat(44100, 2, 12) }
        assertEquals(4, PcmFormat(44100, 2, 16).frameSize)
        assertEquals(44100L, PcmFormat(44100, 1, 16).framesFor(1000))
    }

    // ----------------------------------------------------------------------------------------

    /** Builds a RIFF/WAVE container with arbitrary codec parameters. */
    private fun wavContainer(
        audioFormat: Int,
        channels: Int,
        sampleRate: Int,
        bits: Int,
        data: ByteArray,
    ): ByteArray {
        val frameSize = channels * bits / 8
        val out = ByteArray(44 + data.size)
        writeLittleEndian(out, 0, WavCodec.RIFF_TAG)
        writeLittleEndian(out, 4, 36 + data.size)
        writeLittleEndian(out, 8, WavCodec.WAVE_TAG)
        writeLittleEndian(out, 12, 0x20746D66) // "fmt "
        writeLittleEndian(out, 16, 16)
        writeLittleEndian(out, 20, audioFormat)
        writeLittleEndian(out, 22, channels)
        writeLittleEndian(out, 24, sampleRate)
        writeLittleEndian(out, 28, sampleRate * frameSize)
        writeLittleEndian(out, 32, frameSize)
        writeLittleEndian(out, 34, bits)
        writeLittleEndian(out, 36, 0x61746164) // "data"
        writeLittleEndian(out, 40, data.size)
        data.copyInto(out, 44)
        return out
    }

    private fun writeLittleEndian(bytes: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) bytes[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }
}
