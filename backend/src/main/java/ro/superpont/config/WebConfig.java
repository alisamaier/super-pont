package ro.superpont.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String frontendUrl;

    public WebConfig(@Value("${app.frontend-url}") String frontendUrl) {
        URI origin = URI.create(frontendUrl);
        if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                || origin.getHost() == null || origin.getUserInfo() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty())
                || origin.getQuery() != null || origin.getFragment() != null) {
            throw new IllegalArgumentException("FRONTEND_URL must be an HTTP(S) origin without a trailing slash or path");
        }
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "Accept")
                .maxAge(3600);
    }
}
