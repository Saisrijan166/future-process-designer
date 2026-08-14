package com.assesswise.processdesigner.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the Next.js frontend. Origins come from configuration so the deployed Vercel URL is
 * an environment variable, not a code change.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(AppProperties properties) {
        this.allowedOrigins = properties.cors().allowedOrigins();
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
