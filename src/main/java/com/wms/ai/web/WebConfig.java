package com.wms.ai.web;

import java.io.IOException;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Hosting config for the SPA (README §3.6):
 *
 * <ul>
 *   <li><b>Static serving + history fallback</b> (always on): serves the {@code vite
 *       build} output from {@code classpath:/static/} for a single-origin run, falling
 *       back to {@code index.html} for client-side routes so deep links resolve. When no
 *       built SPA is present the fallback returns nothing (404), unchanged from default.
 *       {@code @RequestMapping} handlers (the {@code /api/**} controllers) are matched
 *       ahead of this resource handler, so the API is never shadowed.
 *   <li><b>CORS</b> (dev profile only): allows the Vite dev server origin
 *       ({@code http://localhost:5173}) as a fallback to the dev proxy. In production the
 *       SPA is same-origin, so no CORS is registered.
 * </ul>
 */
@Component
class WebConfig implements WebMvcConfigurer {

    private static final String VITE_DEV_ORIGIN = "http://localhost:5173";

    private final Environment environment;

    WebConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (environment.matchesProfiles("dev")) {
            registry.addMapping("/api/**")
                    .allowedOrigins(VITE_DEV_ORIGIN)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaPathResourceResolver());
    }

    /**
     * Serves the requested static asset if it exists, otherwise falls back to the SPA's
     * {@code index.html} (so client routes load), and finally {@code null} when no SPA is
     * built — yielding a normal 404 rather than masking missing resources.
     */
    private static final class SpaPathResourceResolver extends PathResourceResolver {

        private static final Resource INDEX = new ClassPathResource("static/index.html");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            return INDEX.exists() ? INDEX : null;
        }
    }
}
