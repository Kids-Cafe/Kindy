package org.kidscafe.kindy.service;

import java.io.InputStream;

/**
 * Where uploaded bytes live.
 *
 * <p>Two implementations, chosen by whether a bucket is configured: S3 with a private bucket, or a
 * directory on this machine. The application has to run with no AWS account at all — the same
 * reason an unconfigured social provider answers NOT_AVAILABLE rather than half-working.
 *
 * <p>A <b>key</b> is the caller's name for the bytes, and is what {@code T_PHOTO.URL} stores. It is
 * identical in both modes, so moving a local deployment to S3 is a bulk copy of the directory into
 * the bucket with nothing to rewrite in the database. Where the bytes physically sit — a configured
 * prefix inside the bucket, a root directory on disk — is this interface's business and never
 * reaches a row.
 *
 * <p>Nothing here hands out a URL. Photos are private to a class, so the only way to reach them is
 * {@code GET /api/class/photo/raw}, which re-checks {@code canViewClass} and then streams whatever
 * {@link #open} returns. That is what makes deleting a photo take effect immediately and a
 * forwarded link worthless to somebody who has left the kindergarten — neither of which a
 * presigned URL can promise, since it stays valid for its whole lifetime no matter what changes.
 */
public interface IStorageService {
    /** Stores the bytes under this key, replacing anything already there. */
    void put(String key, String contentType, byte[] content) throws Exception;

    /**
     * Opens the object for reading, or returns null if there is nothing at this key.
     *
     * <p>The caller owns the stream and must close it. A missing object is null rather than an
     * exception because it is an ordinary outcome: a photo whose row survived a half-failed delete
     * should render as a broken image, not as a 500.
     */
    StoredObject open(String key) throws Exception;

    /** Removes the object. Silent when the key is not there — a delete is idempotent. */
    void delete(String key) throws Exception;

    /**
     * An open object: its bytes, what they are, and how many there are.
     *
     * <p>{@code length} is carried separately so the response can set {@code Content-Length}
     * without buffering the whole photo to measure it, which is the entire reason this is a stream
     * and not a {@code byte[]}.
     *
     * <p>{@code AutoCloseable} so the caller can use try-with-resources: an unclosed stream here is
     * a leaked S3 connection out of a pool that will eventually stop handing them out.
     */
    record StoredObject(InputStream stream, String contentType, long length) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            stream.close();
        }
    }
}
