package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import org.kidscafe.kindy.config.StorageProperties;
import org.kidscafe.kindy.service.IStorageService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Photos in an S3 bucket with Block All Public Access left on.
 *
 * <p>Nothing here produces a URL, and nothing is ever presigned. Objects are read back through
 * {@link #open} and streamed by {@code photo/raw}, which checks the session and {@code canViewClass}
 * first. That costs this application the image bandwidth — which is what the thumbnails are for —
 * and buys two things a presigned URL cannot offer: a deleted photo stops being readable at once
 * rather than when its signature lapses, and a link forwarded outside the kindergarten is worth
 * nothing to whoever receives it.
 *
 * <p>Constructed by {@code StorageConfig} rather than annotated {@code @Service}: which
 * implementation exists is a configuration decision, not a scanning one.
 */
@RequiredArgsConstructor
public class S3StorageService implements IStorageService {
    private final S3Client s3;
    private final StorageProperties.S3 properties;

    @Override
    public void put(String key, String contentType, byte[] content) {
        s3.putObject(b -> b
                        .bucket(properties.getBucket())
                        .key(properties.physicalKey(key))
                        // The type sniffed from the bytes, never the one the uploader claimed —
                        // this is what decides whether a browser paints the photo.
                        .contentType(contentType)
                        // A key is generated per upload and never written twice, so the bytes at one
                        // genuinely cannot change. Without this the object would be revalidated on
                        // every view even though the answer is always the same.
                        .cacheControl("public, max-age=31536000, immutable"),
                RequestBody.fromBytes(content));
    }

    @Override
    public StoredObject open(String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3.getObject(b -> b
                    .bucket(properties.getBucket())
                    .key(properties.physicalKey(key)));

            GetObjectResponse response = stream.response();
            return new StoredObject(stream, response.contentType(), response.contentLength());
        } catch (NoSuchKeyException e) {
            // A row that outlived its object, most likely a delete that half succeeded. The album
            // should show one broken image, not fail the request.
            return null;
        }
    }

    @Override
    public void delete(String key) {
        // S3 answers successfully for a key that is not there, so idempotence comes for free.
        s3.deleteObject(b -> b.bucket(properties.getBucket()).key(properties.physicalKey(key)));
    }
}
