package org.kidscafe.kindy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin access to /api.
 *
 * In development the Vite proxy makes the frontend same-origin, which hides the fact that nothing
 * here was configured; the moment the frontend is served from its own host, every request fails.
 *
 * Authentication is a JSESSIONID cookie, so {@code allowCredentials} has to be on — and with it on
 * the spec forbids a "*" origin, which is why the allowed origins are listed explicitly.
 */
@Configuration
class CorsConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    CorsConfig(@Value("${kindy.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
