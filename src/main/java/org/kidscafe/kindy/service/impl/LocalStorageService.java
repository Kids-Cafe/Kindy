package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.config.StorageProperties;
import org.kidscafe.kindy.service.IStorageService;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Photos on this machine's disk, for when no bucket is configured.
 *
 * <p>The keys are the same strings S3 would use, so the directory tree mirrors the bucket layout
 * exactly and moving a deployment across is a copy with nothing to rewrite in the database.
 *
 * <p>This is not a lesser kind of storage as far as access goes: the bytes are still only reachable
 * through {@code photo/raw}, which checks the session and {@code canViewClass} first. The directory
 * is never exposed as a static resource path — that would hand every photo to anyone who could
 * guess a URL, which is precisely what the proxy exists to prevent.
 *
 * <p>Constructed by {@code StorageConfig} rather than annotated {@code @Service}: which
 * implementation exists is a configuration decision, not a scanning one.
 */
@Slf4j
@RequiredArgsConstructor
public class LocalStorageService implements IStorageService {
    private final StorageProperties.Local properties;

    @Override
    public void put(String key, String contentType, byte[] content) throws Exception {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());

        // Written beside the target and moved into place, so a reader can never see a half-written
        // photo. The move is atomic when the temporary file shares a filesystem with the target,
        // which it does by construction here.
        Path temporary = Files.createTempFile(target.getParent(), ".upload", null);
        try {
            Files.write(temporary, content);
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The content type is taken from what the caller stored rather than probed from the file,
     * because the extension came from sniffed magic bytes in the first place and {@code probeContentType}
     * would only be guessing at the same question from less evidence.
     */
    @Override
    public StoredObject open(String key) throws Exception {
        Path source = resolve(key);
        if (!Files.isRegularFile(source)) return null;

        long length = Files.size(source);
        InputStream stream = Files.newInputStream(source);
        return new StoredObject(stream, contentTypeOf(key), length);
    }

    @Override
    public void delete(String key) throws Exception {
        Files.deleteIfExists(resolve(key));
    }

    /**
     * The file a key names, checked to be inside the root.
     *
     * <p>Keys are built entirely from a parsed class id, the server clock, a UUID and a sniffed
     * extension, so none of them can contain {@code ..} today. The check is here because that is a
     * property of a caller somewhere else, and a path traversal that reaches this class would let
     * one photo overwrite anything the process can write.
     */
    private Path resolve(String key) {
        Path root = properties.getDir().toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();

        if (!target.startsWith(root)) throw new IllegalArgumentException("key escapes the storage root");

        return target;
    }

    private static String contentTypeOf(String key) {
        int dot = key.lastIndexOf('.');
        String extension = dot < 0 ? "" : key.substring(dot + 1).toLowerCase();

        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
