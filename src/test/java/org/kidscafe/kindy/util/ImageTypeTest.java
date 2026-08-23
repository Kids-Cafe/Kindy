package org.kidscafe.kindy.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the sniffer accepts, and — the half that matters — what it turns away.
 *
 * <p>Every rejection here is a file somebody could plausibly upload and expect to work, so each one
 * needs a reason rather than a shrug. The SVG and RIFF cases in particular look like oversights
 * until you know why they are not, which is why they are pinned.
 *
 * <p>The methods under test are pure: no Spring, no filesystem, no collaborators.
 */
class ImageTypeTest {
    /** Builds a header of the given bytes, padded to the twelve the sniffer wants to see. */
    private static byte[] header(int... bytes) {
        byte[] content = new byte[Math.max(bytes.length, 12)];
        for (int i = 0; i < bytes.length; i++) content[i] = (byte) bytes[i];
        return content;
    }

    @Test
    void readsTheFourFormatsItAccepts() {
        assertEquals(ImageType.JPEG, ImageType.sniff(header(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals(ImageType.PNG, ImageType.sniff(header(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        assertEquals(ImageType.GIF, ImageType.sniff(header('G', 'I', 'F', '8', '9', 'a')));
        assertEquals(ImageType.WEBP, ImageType.sniff(
                header('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
    }

    @Test
    void namesTheStoredObjectAndItsTypeFromWhatItFound() {
        assertEquals("jpg", ImageType.JPEG.extension());
        assertEquals("image/jpeg", ImageType.JPEG.contentType());
        assertEquals("png", ImageType.PNG.extension());
        assertEquals("image/png", ImageType.PNG.contentType());
        assertEquals("gif", ImageType.GIF.extension());
        assertEquals("image/gif", ImageType.GIF.contentType());
        assertEquals("webp", ImageType.WEBP.extension());
        assertEquals("image/webp", ImageType.WEBP.contentType());
    }

    @Test
    void refusesAFileThatOnlyClaimsToBeAnImage() {
        // An SVG is a script host and these bytes are served back from our own origin with the
        // session cookie, so accepting one would be stored XSS. Not an oversight.
        assertNull(ImageType.sniff("<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>"
                .getBytes(StandardCharsets.UTF_8)));

        assertNull(ImageType.sniff("%PDF-1.4\n%âãÏÓ".getBytes(StandardCharsets.ISO_8859_1)));
        assertNull(ImageType.sniff(header('P', 'K', 0x03, 0x04)));           // a renamed .docx
        assertNull(ImageType.sniff("안녕하세요, 사진이 아닙니다.".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageType.sniff(header(0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c')));
    }

    @Test
    void refusesARiffThatIsNotWebp() {
        // RIFF is a container marker shared with .wav and .avi. A four-byte check — the obvious way
        // to write this — would store an audio file as an image.
        assertNull(ImageType.sniff(header('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E')));
        assertNull(ImageType.sniff(header('R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' ')));
    }

    @Test
    void refusesSomethingTooShortToTell() {
        assertNull(ImageType.sniff(null));
        assertNull(ImageType.sniff(new byte[0]));
        assertNull(ImageType.sniff(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        // Eleven bytes of a genuine WebP: one short of the second half it takes to be sure.
        assertNull(ImageType.sniff(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B'}));
    }
}
