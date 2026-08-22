package org.kidscafe.kindy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the single-page app's client-side routes.
 *
 * <p>The frontend is built into this JAR's static resources and is a single page: the browser
 * fetches {@code index.html} once and React decides what to draw from {@code window.location}.
 * The server never had a file at {@code /oauth/callback}, so a request straight to that path —
 * which is exactly what an authorization server sends the user back to, and what a refresh or a
 * bookmark produces — would 404 before React ever ran. Forwarding those paths to
 * {@code index.html} lets the app boot and read the URL itself.
 *
 * <p>Two things are deliberately left alone:
 * <ul>
 *   <li><b>{@code /api/**}</b> keeps answering for itself. Forwarding it would turn every
 *       mistyped endpoint into a 200 with an HTML body, which the frontend's fetch wrapper would
 *       meet as a JSON parse error rather than the 404 it is.</li>
 *   <li><b>Paths with a dot</b> are treated as files. Without that, a missing
 *       {@code /assets/app-a1b2c3.js} would return HTML with a 200 and the browser would report a
 *       syntax error in a script that is actually not there — a genuinely confusing way to
 *       discover a broken build.</li>
 * </ul>
 *
 * <p>The patterns below match one and two path segments, which covers the app's routes. A deeper
 * client route would need another line; matching everything with {@code /**} would swallow the two
 * exceptions above.
 */
@Configuration
class SpaForwardingConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{path:^(?!api$).*}/{sub:[^\\.]*}").setViewName("forward:/index.html");
    }
}
