package org.kidscafe.kindy.util;

/**
 * What an uploaded file actually is, decided by its first bytes rather than by what it claims.
 *
 * <p>The {@code Content-Type} header and the filename both arrive inside a multipart body that
 * anything can construct, so neither is evidence. What matters is that the type we sniff here is the
 * type we store on the object, and that stored type is what decides whether a browser paints the
 * photo or offers to download it. Trusting the uploader's word would let them choose that.
 *
 * <p>SVG is missing from this list on purpose, and the omission is load-bearing. An SVG is a script
 * host, and photos are served back from {@code /api/class/photo/raw} — this application's own
 * origin, with the session cookie attached — so accepting one would be stored XSS against every
 * parent who opened the album. The same reasoning rules out HTML and anything else whose rendering
 * depends on who is asking.
 *
 * <p>HEIC is rejected as a consequence: no browser renders it. iOS transcodes to JPEG when the photo
 * goes through {@code <input accept="image/*">}, so the common path is unaffected, but someone
 * picking a {@code .heic} out of a synced folder on a desktop will be turned away — which is why the
 * error message names the four formats instead of saying "images only".
 *
 * <p>Static rather than a {@code @Component} because it holds no configuration, matching
 * {@link PkceUtil}.
 */
public enum ImageType {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    /** Enough bytes to decide any of the four. WebP is the longest look-ahead, needing twelve. */
    private static final int HEADER_LENGTH = 12;

    private final String extension;
    private final String contentType;

    ImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    /** The extension used when naming the stored object. Lower case, no dot. */
    public String extension() {
        return extension;
    }

    /** The type set on the stored object, and sent back when it is served. */
    public String contentType() {
        return contentType;
    }

    /**
     * The type these bytes really are, or null if they are not one of the four.
     *
     * <p>Anything shorter than a header is unrecognisable rather than an error — a truncated upload
     * and a text file are the same answer here.
     */
    public static ImageType sniff(byte[] content) {
        if (content == null || content.length < HEADER_LENGTH) return null;

        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) return JPEG;
        if (startsWith(content, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return PNG;
        if (startsWith(content, 0, 'G', 'I', 'F', '8')) return GIF;

        // Both halves are required. RIFF alone is a container marker shared with .wav and .avi, so a
        // four-byte check here would accept an audio file and store it as an image.
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P'))
            return WEBP;

        return null;
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
