package org.kidscafe.kindy.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * A small copy of a photo, for the album grid.
 *
 * <p>Photos are served through this application rather than straight from storage, so the grid is
 * the one place where full-resolution originals would actually cost something: twenty tiles at
 * 150px, painted from twenty files of up to 8MB, is over a hundred megabytes moved through the
 * server to show thumbnails. At 400px the same grid costs about a megabyte. The original is fetched
 * only when someone opens the viewer.
 *
 * <p>Three things about {@link ImageIO} shape what this can promise, and all three are easier to
 * design around than to discover later:
 *
 * <ul>
 *   <li><b>It cannot read WebP.</b> Java 17 ships readers for JPEG, PNG, GIF, BMP and TIFF and no
 *       more. So this returns null rather than throwing, the caller stores no thumbnail, and the
 *       grid falls back to the original for that one photo. One format loading full-size is a cost
 *       worth paying; refusing the upload is not.</li>
 *   <li><b>Transparency does not survive JPEG.</b> A PNG re-encoded as JPEG gets black wherever it
 *       was transparent, so PNGs stay PNGs and everything else becomes JPEG.</li>
 *   <li><b>EXIF orientation is ignored.</b> A phone JPEG carrying a rotation tag is decoded
 *       unrotated, so its thumbnail can sit sideways next to an upright original. Fixing that needs
 *       an EXIF parse; it is a known limitation rather than an accident.</li>
 * </ul>
 *
 * <p>Static rather than a {@code @Component} because it holds no configuration, matching
 * {@link PkceUtil}.
 */
@Slf4j
public final class Thumbnailer {
    /** Longest edge of the result. The grid paints at ~150px; this survives a 2x display. */
    public static final int MAX_EDGE = 400;

    private Thumbnailer() {}

    /**
     * A downscaled copy, or null when one cannot be made.
     *
     * <p>Null is an ordinary answer, not a failure: WebP has no reader, and a file that sniffed as
     * an image can still be truncated past its header. Either way the caller keeps the original and
     * carries on — a missing thumbnail costs bandwidth, a failed upload costs the photo.
     *
     * <p>An image already smaller than {@link #MAX_EDGE} is still re-encoded rather than returned
     * as-is, because the caller stores whatever comes back under the thumbnail key and the two
     * copies have to be independently deletable.
     */
    public static Result thumbnail(byte[] content, ImageType type) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            // Null means no reader claimed these bytes — WebP always, or a file that is damaged
            // past the header we sniffed.
            if (source == null) return null;

            // PNG keeps its alpha channel; everything else is flattened onto white rather than the
            // black an unpainted JPEG raster would default to.
            boolean png = type == ImageType.PNG;
            BufferedImage scaled = scale(source, png);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(scaled, png ? "png" : "jpg", out)) return null;

            ImageType resultType = png ? ImageType.PNG : ImageType.JPEG;
            return new Result(out.toByteArray(), resultType);
        } catch (Exception e) {
            // Never fatal to the upload. The photo itself is already fine.
            log.warn("Could not make a thumbnail", e);
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage source, boolean keepAlpha) {
        int width = source.getWidth();
        int height = source.getHeight();
        double factor = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));

        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);

        Graphics2D g = target.createGraphics();
        try {
            if (!keepAlpha) {
                // A JPEG raster starts black, so anything transparent in the source would come out
                // black rather than simply losing its transparency.
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, targetWidth, targetHeight);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        return target;
    }

    /** The thumbnail's bytes and the type they were written as, which is not always the source's. */
    public record Result(byte[] content, ImageType type) {}
}
