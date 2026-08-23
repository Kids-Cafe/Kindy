package org.kidscafe.kindy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Where uploaded photos go.
 *
 * <p>A blank bucket means S3 is not configured and photos are written to a directory instead, so a
 * checkout runs with no AWS account. That mirrors {@link OAuthProperties#isConfigured}: blank rather
 * than absent, and the blankness is what the decision is made on.
 *
 * <p>Nothing credential-shaped lives here. The SDK's default provider chain reads the environment,
 * system properties, {@code ~/.aws} and then the container or instance role — an access key written
 * into a property file is an access key inside the JAR.
 */
@Component
@ConfigurationProperties(prefix = "kindy.storage")
public class StorageProperties {
    private final S3 s3 = new S3();
    private final Local local = new Local();

    public S3 getS3() { return s3; }
    public Local getLocal() { return local; }

    /** True when a bucket is configured and photos should go to S3. */
    public boolean isS3Configured() {
        return s3.getBucket() != null && !s3.getBucket().isBlank();
    }

    public static class S3 {
        private String bucket = "";
        private String region = "ap-northeast-2";

        /**
         * Prefixed onto every key inside the bucket, so one bucket can hold more than one
         * deployment. Deliberately not part of the key stored in a row: changing it moves objects,
         * not rows.
         */
        private String keyPrefix = "";

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

        /** The key with the deployment prefix applied, normalised so it always ends in one slash. */
        public String physicalKey(String key) {
            if (keyPrefix == null || keyPrefix.isBlank()) return key;
            return keyPrefix.endsWith("/") ? keyPrefix + key : keyPrefix + "/" + key;
        }
    }

    public static class Local {
        private Path dir = Path.of("./data/photos");

        public Path getDir() { return dir; }
        public void setDir(Path dir) { this.dir = dir; }
    }
}
