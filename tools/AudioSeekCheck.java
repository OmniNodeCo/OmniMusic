import co.omnimusic.core.audio.AudioSource;
import co.omnimusic.core.desktop.JavaSoundAudioOutput;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Seeks a real MP3 with the classes the packaged app actually ships.
 *
 * This exists because the unit tests can only decode WAV: the JDK reads WAV natively, and the mp3spi
 * jar that decodes MP3 is on the Gradle classpath, not the kotlinc one. The bug this guards against
 * was MP3-specific — an MP3 arrives as a conversion stream over a decoder with its own state, and
 * rewinding it is not something the SPI honours — so a WAV-only test suite could not see it either
 * way. Run by tools/smoke-audio-seek.sh against the app image, where the decoder is present.
 *
 * Written in Java because a CI runner has javac and no standalone kotlinc.
 */
public final class AudioSeekCheck {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AudioSeekCheck <mp3 url>");
            System.exit(2);
        }
        String url = args[0];
        JavaSoundAudioOutput output = new JavaSoundAudioOutput(8192);

        output.openSource(new AudioSource.Remote(url));
        System.out.println("opened the remote MP3 through the packaged decoder");

        // 1. What the player does now: two independent decoders over bytes already in memory.
        byte[] fromStart = readFrom(output.reopenedAt(0), 4096);
        byte[] fromThirty = readFrom(output.reopenedAt(30_000), 4096);
        require(fromStart.length == 4096, "the decoder produced no audio at the start");
        require(fromThirty.length == 4096, "the decoder produced no audio 30 s in");
        require(!java.util.Arrays.equals(fromStart, fromThirty),
                "seeking 30 s in returned the same audio as the start - the seek did nothing");
        System.out.println("PASS: re-decoding lands 30 s in, on different audio");

        // 2. What the player used to do: rewind the one stream. This is the failure being replaced,
        //    demonstrated rather than assumed.
        AudioInputStream legacy = AudioSystem.getAudioInputStream(new URL(url));
        byte[] sink = new byte[8192];
        for (int i = 0; i < 16; i++) {
            if (legacy.read(sink) <= 0) break;
        }
        String verdict;
        try {
            legacy.reset();
            verdict = "reset() unexpectedly succeeded; the old code path may work on this decoder";
        } catch (Exception e) {
            verdict = "reset() threw " + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + " - which is the \"cannot seek this stream\" the fix removes";
        }
        System.out.println("legacy rewind: " + verdict);
        System.out.println("OK");
    }

    private static byte[] readFrom(AudioInputStream stream, int count) throws Exception {
        if (stream == null) throw new IllegalStateException("no stream to read from");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (out.size() < count) {
            int read = stream.read(buffer, 0, Math.min(buffer.length, count - out.size()));
            if (read <= 0) break;
            out.write(buffer, 0, read);
        }
        stream.close();
        return out.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            System.err.println("FAIL: " + message);
            System.exit(1);
        }
    }
}
