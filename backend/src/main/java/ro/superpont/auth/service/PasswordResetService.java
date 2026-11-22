package ro.superpont.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.auth.model.PasswordResetToken;
import ro.superpont.auth.repository.PasswordResetTokenRepository;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.auth.security.AuthRateLimiter;
import ro.superpont.auth.validation.EmailAddress;
import ro.superpont.common.DomainException;

@Service
public class PasswordResetService {
    public static final String CONFIRMATION = "Dacă există un cont asociat acestei adrese, vei primi un email cu instrucțiunile pentru resetarea parolei.";
    public static final String INVALID_LINK = "Linkul este invalid sau a expirat. Solicită un link nou.";
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final PasswordResetEmailSender mail;
    private final AuthRateLimiter limits;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder passwords,
            PasswordResetEmailSender mail, AuthRateLimiter limits, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.mail = mail;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional
    public void request(String email) {
        String normalized = EmailAddress.normalize(email);
        limits.checkPasswordResetEmail(normalized);
        var id = users.findIdByEmail(normalized);
        if (id.isEmpty()) return;
        var user = users.findForUpdateById(id.get()).orElse(null);
        if (user == null || !user.getEmail().equals(normalized)) return;
        var bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expires = clock.instant().plus(Duration.ofMinutes(30));
        var reset = tokens.saveAndFlush(new PasswordResetToken(user.getId(), digest(token), expires));
        try {
            mail.send(user.getEmail(), token, expires);
        } catch (MailException exception) {
            // Preserve all earlier links and credentials; never log SMTP content or expose account existence.
            reset.consume(clock.instant());
        }
    }

    @Transactional(readOnly = true)
    public void validate(String token) {
        String hash = checkedHash(token);
        if (!tokens.findByTokenHash(hash).filter(reset -> reset.usableAt(clock.instant())).isPresent()) throw invalid();
    }

    @Transactional
    public void reset(String token, String password) {
        if (!UserService.fitsBcrypt(password)) throw DomainException.invalid("Password must be at most 72 UTF-8 bytes");
        String hash = checkedHash(token);
        var userId = tokens.findUserIdByTokenHash(hash).orElseThrow(PasswordResetService::invalid);
        // Every issuer and consumer locks the account first. Different links for the same user serialize too.
        var user = users.findForUpdateById(userId).orElseThrow(PasswordResetService::invalid);
        var reset = tokens.findByTokenHash(hash).orElseThrow(PasswordResetService::invalid);
        if (!reset.usableAt(clock.instant())) throw invalid();
        var encoded = passwords.encode(password);
        // A slow password hash cannot extend the lifetime of a link.
        var now = clock.instant();
        if (!reset.usableAt(now)) throw invalid();
        user.changePassword(encoded); // Persisted password version revokes sessions on every app instance.
        tokens.consumeAll(userId, now);
    }

    private String checkedHash(String token) {
        String hash = digest(token);
        limits.checkPasswordResetToken(hash);
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        return hash;
    }

    private static DomainException invalid() { return DomainException.invalid(INVALID_LINK); }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
