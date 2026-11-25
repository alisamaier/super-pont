package ro.superpont.football.team.service;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ro.superpont.football.team.model.TeamLogoProperties;
import ro.superpont.football.sportstats.client.SportStatsException;

@Component
@ConditionalOnProperty(name = "app.team-logos.storage", havingValue = "filesystem", matchIfMissing = true)
public class FileTeamLogoStore implements TeamLogoStore {
    private final Path root;
    public FileTeamLogoStore(TeamLogoProperties properties) { root = properties.cacheDir().toAbsolutePath().normalize(); }

    public Path path(String hash) {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new SportStatsException("INVALID_CACHE_KEY");
        return root.resolve(hash + ".png");
    }
    public boolean contains(String hash) { return hash != null && Files.isRegularFile(path(hash)); }
    public byte[] read(String hash) {
        try { return Files.readAllBytes(path(hash)); }
        catch (IOException e) { throw new SportStatsException("LOGO_CACHE_FAILED"); }
    }
    public void write(String hash, byte[] png) {
        try {
            Files.createDirectories(root);
            if (contains(hash)) return;
            Path temp = Files.createTempFile(root, ".logo-", ".tmp");
            try {
                Files.write(temp, png);
                try { Files.move(temp, path(hash), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, path(hash), StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new SportStatsException("LOGO_CACHE_FAILED"); }
    }
}
