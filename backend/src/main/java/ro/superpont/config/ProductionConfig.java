package ro.superpont.config;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import org.springframework.core.env.Environment;

/** Fail closed on dangerous production overrides; messages never include configuration values. */
public class ProductionConfig {
    public ProductionConfig(Environment env) {
        require(!Arrays.asList(env.getActiveProfiles()).contains("dev"), "prod cannot be combined with dev");
        String database = env.getRequiredProperty("spring.datasource.url");
        require(verifiedPostgres(database),
                "Production DATABASE_URL requires PostgreSQL with verify-full and DefaultJavaSSLFactory");
        require("classpath:db/migration".equals(env.getRequiredProperty("spring.flyway.locations")),
                "Production Flyway must use only classpath:db/migration");
        require("database".equals(env.getRequiredProperty("app.team-logos.storage")), "Production logos require database storage");
        require(!env.getProperty("app.sportstats.sync-enabled", Boolean.class, false)
                && !env.getProperty("app.team-logos.automatic-enabled", Boolean.class, false),
                "Production imports must remain manual");
        for (String key : new String[] {"app.frontend-url", "app.password-reset.frontend-url"}) {
            URI origin = URI.create(env.getRequiredProperty(key));
            require("https".equals(origin.getScheme()) && origin.getHost() != null && !origin.getHost().equals("localhost")
                    && origin.getUserInfo() == null && origin.getRawPath().isEmpty()
                    && origin.getRawQuery() == null && origin.getRawFragment() == null,
                    "Production frontend URLs must be exact public HTTPS origins");
        }
        require(env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false), "Production cookies require Secure");
        require("lax".equalsIgnoreCase(env.getRequiredProperty("server.servlet.session.cookie.same-site")),
                "Production uses SameSite=Lax with the frontend API proxy");
        String from = env.getRequiredProperty("app.mail.from");
        require(!from.isBlank() && !from.contains("localhost") && !from.contains(".local>")
                && !from.contains(".example"), "Production MAIL_FROM requires a verified real sender");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static boolean verifiedPostgres(String database) {
        if (!database.startsWith("jdbc:postgresql://")) return false;
        try {
            var uri = URI.create(database.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getRawQuery() == null) return false;
            var parameters = new HashMap<String, String>();
            for (String part : uri.getRawQuery().split("&")) {
                var pair = part.split("=", 2);
                if (pair.length != 2 || parameters.putIfAbsent(pair[0], pair[1]) != null) return false;
            }
            return "verify-full".equals(parameters.get("sslmode"))
                    && "org.postgresql.ssl.DefaultJavaSSLFactory".equals(parameters.get("sslfactory"));
        } catch (IllegalArgumentException e) { return false; }
    }
}
