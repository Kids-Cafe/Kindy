package org.kidscafe.kindy.service;

/**
 * Thrown when a feature was asked for that this deployment has not configured.
 *
 * <p>The speech and language services are declared blank in application.properties, so an
 * application with no model server still starts — the same bargain {@code kindy.oauth.*.client-id}
 * and {@code kindy.storage.s3.bucket} already make. What is left is telling the two apart at the
 * moment of use: a service that was never configured and a service that is configured but down are
 * different problems with different fixes, and without this they arrive at the client as the same
 * GENERATION_FAILED.
 *
 * <p>Unchecked, because it is a deployment mistake rather than a condition a caller can handle —
 * every intermediate method between the controller and the outbound call would otherwise have to
 * declare it. Controllers catch it and answer NOT_AVAILABLE, matching OAuthController.
 *
 * <p>It deliberately does <b>not</b> extend {@link IllegalArgumentException}. Several controller
 * methods catch that first and map it to INVALID_PARAMETER, which would blame the caller's request
 * for something no request could have got right.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
