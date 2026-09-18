package co.omnimusic.core.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The decode-to-PCM decision, without an audio device: these only build [AudioFormat] objects.
 *
 * They pin the behaviour that makes MP3 playback work at all — an SPI hands back `MPEG1L3`, and if
 * that reaches the output line the failure looks like a missing sound card rather than a missing
 * decoder.
 */
class PcmConversionTest {

    private val mpeg = AudioFormat(AudioFormat.Encoding("MPEG1L3"), 44_100f, 16, 2, 4, 44_100f, false)
    private val pcmSigned = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44_100f, 16, 2, 4, 44_100f, false)
    private val pcmUnsigned = AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 22_050f, 8, 1, 1, 22_050f, true)
    private val pcmFloat = AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 48_000f, 32, 2, 8, 48_000f, false)

    fun testNativePcmEncodingsNeedNoConversion() {
        assertFalse(PcmConversion.needsConversion(pcmSigned))
        assertFalse(PcmConversion.needsConversion(pcmUnsigned))
        assertFalse(PcmConversion.needsConversion(pcmFloat))
    }

    fun testASpiEncodingNeedsConversion() {
        assertTrue(PcmConversion.needsConversion(mpeg))
        assertTrue(PcmConversion.needsConversion(AudioFormat(AudioFormat.Encoding("VORBIS"), 44_100f, 16, 2, 4, 44_100f, false)))
    }

    fun testAnInventedPcmEncodingIsAcceptedAsIs() {
        val odd = AudioFormat(AudioFormat.Encoding("PCM_FLOAT_LE"), 48_000f, 32, 2, 8, 48_000f, false)
        assertFalse(PcmConversion.needsConversion(odd))
    }

    fun testTheRequestedTargetIsLittleEndianSixteenBitPcmAtTheSourceRate() {
        val target = assertNotNull(PcmConversion.targetFor(mpeg))
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, target.encoding)
        assertEquals(44_100f, target.sampleRate)
        assertEquals(2, target.channels)
        assertEquals(16, target.sampleSizeInBits)
        assertEquals(4, target.frameSize)
        assertFalse(target.isBigEndian)
    }

    fun testMonoStaysMono() {
        val mono = AudioFormat(AudioFormat.Encoding("MPEG1L3"), 22_050f, 16, 1, 2, 22_050f, false)
        val target = assertNotNull(PcmConversion.targetFor(mono))
        assertEquals(1, target.channels)
        assertEquals(2, target.frameSize)
    }

    fun testAnIncompleteFormatYieldsNoTargetInsteadOfAnAbsurdOne() {
        // Some decoders report NOT_SPECIFIED until the first frame is parsed.
        val unknown = AudioFormat(AudioFormat.Encoding("MPEG1L3"), AudioSystem.NOT_SPECIFIED.toFloat(), 16, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED.toFloat(), false)
        assertNull(PcmConversion.targetFor(unknown))
    }
}
