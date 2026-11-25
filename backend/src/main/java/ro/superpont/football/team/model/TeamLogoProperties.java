package ro.superpont.football.team.model;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.team-logos")
public record TeamLogoProperties(Path cacheDir) {}
