package org.kidscafe.kindy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.service.IStorageService;
import org.kidscafe.kindy.service.impl.LocalStorageService;
import org.kidscafe.kindy.service.impl.S3StorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Picks the storage implementation, once, from whether a bucket is configured.
 *
 * <p>The condition tests for a bucket that is <b>not blank</b> rather than one that is present, and
 * that distinction is the whole reason this is written out rather than left to a pair of
 * {@code @ConditionalOnProperty} annotations. The property is declared {@code ${S3_BUCKET:}}, so an
 * unconfigured deployment still <i>has</i> it — presence is all {@code @ConditionalOnProperty}
 * checks, so it would select S3 for a deployment with no bucket and fail on the first upload.
 *
 * <p>The choice between the two is made in one place for a second reason: two conditions kept
 * exactly opposite by hand eventually both match, or neither does, and the failure arrives as "no
 * qualifying bean" a long way from the property that caused it.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
class StorageConfig {
    private final StorageProperties properties;

    /**
     * The default provider chain: environment, system properties, {@code ~/.aws}, then the
     * container or instance role. Nothing about credentials is configurable here on purpose — an
     * access key in a property file is an access key inside the JAR.
     */
    @Bean
    @ConditionalOnExpression("!'${kindy.storage.s3.bucket:}'.isBlank()")
    AwsCredentialsProvider awsCredentialsProvider() {
        // builder().build() rather than the deprecated create(): same chain, and the SDK keeps this
        // one.
        return DefaultCredentialsProvider.builder().build();
    }

    /**
     * Its own bean, rather than something built inside {@link #storageService}, so Spring's inferred
     * {@code destroyMethod} closes it on shutdown instead of leaking the connection pool.
     */
    @Bean
    @ConditionalOnExpression("!'${kindy.storage.s3.bucket:}'.isBlank()")
    S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(properties.getS3().getRegion()))
                .credentialsProvider(credentialsProvider)
                // Stated rather than discovered. Both default transports are excluded in
                // build.gradle, so naming it here makes removing that dependency a compile error
                // instead of a startup one.
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    IStorageService storageService(ObjectProvider<S3Client> s3Client) {
        S3Client client = s3Client.getIfAvailable();

        if (client == null) {
            log.info("No S3 bucket configured — photos are stored under {}", properties.getLocal().getDir());
            return new LocalStorageService(properties.getLocal());
        }

        log.info("Photos are stored in the S3 bucket {}", properties.getS3().getBucket());
        return new S3StorageService(client, properties.getS3());
    }
}
