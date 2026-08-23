package org.kidscafe.kindy.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the grid gets, and what happens when a thumbnail cannot be made at all.
 *
 * <p>The second half is the point. Thumbnailing is best effort by design — a photo whose thumbnail
 * fails must still upload — so the tests that matter most are the ones asserting a null answer
 * instead of an exception.
 *
 * <p>Images are generated here rather than checked in as fixtures: the assertions are about size
 * and format, and a real photo would only make the failure messages harder to read.
 */
class ThumbnailerTest {
    private static byte[] image(int width, int height, String format, boolean transparent) throws IOException {
        BufferedImage source = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(transparent ? new Color(0, 0, 0, 0) : Color.PINK);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(source, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] content) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(content));
    }

    @Test
    void bringsALargePhotoDownToTheGridSize() throws IOException {
        Thumbnailer.Result result = Thumbnailer.thumbnail(image(2400, 1600, "jpg", false), ImageType.JPEG);

        assertNotNull(result);
        BufferedImage thumb = decode(result.content());
        assertEquals(Thumbnailer.MAX_EDGE, thumb.getWidth());
        assertEquals(267, thumb.getHeight());          // 1600 * (400/2400), rounded
        assertTrue(result.content().length < 100_000, "a 400px thumbnail should be small");
    }

    @Test
    void keepsThePhotoTheRightWayUp() throws IOException {
        Thumbnailer.Result result = Thumbnailer.thumbnail(image(1000, 2000, "jpg", false), ImageType.JPEG);

        assertNotNull(result);
        BufferedImage thumb = decode(result.content());
        assertEquals(200, thumb.getWidth());
        assertEquals(Thumbnailer.MAX_EDGE, thumb.getHeight());
    }

    @Test
    void writesAPngAsAPngSoTransparencySurvives() throws IOException {
        // A transparent PNG re-encoded as JPEG comes back black, which is worse than not shrinking
        // it at all — hence the one branch in the scaler.
        Thumbnailer.Result result = Thumbnailer.thumbnail(image(800, 800, "png", true), ImageType.PNG);

        assertNotNull(result);
        assertEquals(ImageType.PNG, result.type());
        assertEquals(ImageType.PNG, ImageType.sniff(result.content()));
        assertTrue(decode(result.content()).getColorModel().hasAlpha());
    }

    @Test
    void writesEverythingElseAsAJpeg() throws IOException {
        Thumbnailer.Result fromGif = Thumbnailer.thumbnail(image(600, 600, "gif", false), ImageType.GIF);

        assertNotNull(fromGif);
        assertEquals(ImageType.JPEG, fromGif.type());
        assertEquals(ImageType.JPEG, ImageType.sniff(fromGif.content()));
    }

    @Test
    void reEncodesEvenWhenThePhotoIsAlreadySmall() throws IOException {
        // The caller stores whatever comes back under the thumbnail key, so the two copies have to
        // be separate objects even when they would look identical.
        Thumbnailer.Result result = Thumbnailer.thumbnail(image(120, 90, "jpg", false), ImageType.JPEG);

        assertNotNull(result);
        BufferedImage thumb = decode(result.content());
        assertEquals(120, thumb.getWidth());
        assertEquals(90, thumb.getHeight());
    }

    @Test
    void givesUpQuietlyOnAFormatImageIoCannotRead() {
        // Java 17 ships no WebP reader. The upload still has to succeed, so this is null and not an
        // exception — the caller stores no thumbnail and the grid uses the original.
        byte[] webp = new byte[64];
        System.arraycopy(new byte[]{'R', 'I', 'F', 'F'}, 0, webp, 0, 4);
        System.arraycopy(new byte[]{'W', 'E', 'B', 'P'}, 0, webp, 8, 4);

        assertEquals(ImageType.WEBP, ImageType.sniff(webp), "the sniffer accepts it");
        assertNull(Thumbnailer.thumbnail(webp, ImageType.WEBP), "but no thumbnail can be made");
    }

    @Test
    void givesUpQuietlyOnBytesThatAreDamagedPastTheHeader() {
        byte[] truncated = new byte[64];
        System.arraycopy(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 0, truncated, 0, 4);

        assertEquals(ImageType.JPEG, ImageType.sniff(truncated), "it sniffs as a JPEG");
        assertNull(Thumbnailer.thumbnail(truncated, ImageType.JPEG), "but it will not decode");
    }
}
