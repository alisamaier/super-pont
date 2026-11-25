package ro.superpont.football.team.service;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import ro.superpont.football.sportstats.client.SportStatsException;

/** Only decoded raster data is published; never serve untrusted upstream bytes or filenames. */
@Component
public class TeamLogoCache {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private final TeamLogoStore storage;
    public TeamLogoCache(TeamLogoStore storage) { this.storage = storage; }

    private void validateHash(String hash) {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new SportStatsException("INVALID_CACHE_KEY");
    }
    public boolean contains(String hash) {
        if (hash == null) return false;
        validateHash(hash);
        return storage.contains(hash);
    }
    public byte[] read(String hash) { validateHash(hash); return storage.read(hash); }

    public String store(byte[] bytes, String type) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES || !Set.of("image/png", "image/jpeg", "image/webp").contains(type))
            throw new SportStatsException("INVALID_LOGO_IMAGE");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new SportStatsException("INVALID_LOGO_IMAGE");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String expected = Map.of("png", "image/png", "jpeg", "image/jpeg", "webp", "image/webp").get(format);
                if (!type.equals(expected)) throw new SportStatsException("INVALID_LOGO_IMAGE");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 2048 || height > 2048 || (long) width * height > 4_000_000)
                    throw new SportStatsException("INVALID_LOGO_DIMENSIONS");
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output)) throw new SportStatsException("INVALID_LOGO_IMAGE");
                byte[] png = output.toByteArray();
                if (png.length > MAX_BYTES) throw new SportStatsException("INVALID_LOGO_IMAGE");
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
                storage.write(hash, png);
                return hash;
            } finally { reader.dispose(); }
        } catch (SportStatsException e) { throw e; }
        catch (Exception e) { throw new SportStatsException("LOGO_CACHE_FAILED"); }
    }
}
