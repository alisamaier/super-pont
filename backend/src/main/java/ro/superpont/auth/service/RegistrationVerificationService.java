package ro.superpont.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.auth.dto.PendingRegistrationView;
import ro.superpont.auth.model.RegistrationVerification;
import ro.superpont.auth.model.User;
import ro.superpont.auth.repository.RegistrationVerificationRepository;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.auth.security.AuthRateLimitException;
import ro.superpont.auth.security.AuthRateLimitProperties;
import ro.superpont.auth.security.AuthRateLimiter;
import ro.superpont.auth.validation.EmailAddress;
import ro.superpont.common.DomainException;

@Service
public class RegistrationVerificationService {
    private final UserRepository users;
    private final RegistrationVerificationRepository registrations;
    private final PasswordEncoder passwords;
    private final VerificationEmailSender email;
    private final AuthRateLimiter limits;
    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RegistrationVerificationService(UserRepository users, RegistrationVerificationRepository registrations,
            PasswordEncoder passwords, VerificationEmailSender email, AuthRateLimiter limits,
            AuthRateLimitProperties properties, Clock clock) {
        this.users = users;
        this.registrations = registrations;
        this.passwords = passwords;
        this.email = email;
        this.limits = limits;
        this.properties = properties;
        this.clock = clock;
    }

    // This result remains inside the server; only the HttpOnly cookie receives the token.
    public record Started(String token, PendingRegistrationView view) {}

    @Transactional
    public Started start(UUID userId, String address) {
        var user = users.findForUpdateById(userId).orElseThrow(RegistrationVerificationService::expired);
        return start(user, address);
    }

    @Transactional
    public Started resume(String address, String password, String ip) {
        var candidate = users.findIdByEmail(EmailAddress.normalize(address))
                .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        var user = users.findForUpdateById(candidate).orElseThrow(RegistrationVerificationService::expired);
        if (!user.getEmail().equals(EmailAddress.normalize(address)))
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        if (!UserService.fitsBcrypt(password) || !passwords.matches(password, user.getPasswordHash()))
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        return start(user, ip);
    }

    private Started start(User user, String address) {
        var now = clock.instant();
        var existing = registrations.findById(user.getId());
        var registration = existing.orElseGet(() -> new RegistrationVerification(user.getId(), now));
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        registration.rotateToken(digest(token), now);
        // A login resumes the existing code and counters. It cannot bypass resend limits.
        if (!user.isEmailVerified() && existing.isEmpty()) {
            try {
                checkSending(registration, address, now);
                send(user, registration, now);
            } catch (AuthRateLimitException limited) {
                registration.deferSending(now.plusSeconds(limited.getRetryAfter()));
            }
        }
        registrations.save(registration);
        return new Started(token, view(user, registration, clock.instant()));
    }

    @Transactional(readOnly = true)
    public PendingRegistrationView status(String token) {
        var registration = authorized(token);
        var user = users.findById(registration.getUserId()).orElseThrow(RegistrationVerificationService::expired);
        return view(user, registration, clock.instant());
    }

    // All mutations lock the user first. The same lock serializes confirmation, resend,
    // email correction and login token rotation, including across application instances.
    @Transactional(noRollbackFor = VerificationException.class)
    public PendingRegistrationView confirm(String token, String code) {
        var registration = locked(token);
        var user = users.findById(registration.getUserId()).orElseThrow(RegistrationVerificationService::expired);
        var now = clock.instant();
        if (user.isEmailVerified()) return view(user, registration, clock.instant());
        limits.checkVerificationRegistration(user.getId().toString());
        if (registration.getFailedAttempts() >= 5)
            throw new VerificationException(HttpStatus.CONFLICT, "CODE_ATTEMPTS_EXHAUSTED", 0);
        if (registration.getCodeHash() == null || !now.isBefore(registration.getCodeExpiresAt()))
            throw new VerificationException(HttpStatus.GONE, "CODE_EXPIRED", 5 - registration.getFailedAttempts());
        if (!passwords.matches(code, registration.getCodeHash())) {
            registration.failAttempt();
            throw new VerificationException(HttpStatus.BAD_REQUEST,
                    registration.getFailedAttempts() == 5 ? "CODE_ATTEMPTS_EXHAUSTED" : "INCORRECT_CODE",
                    5 - registration.getFailedAttempts());
        }
        user.verifyEmail();
        registration.consumeCode();
        return view(user, registration, clock.instant());
    }

    @Transactional
    public PendingRegistrationView resend(String token, String address) {
        var registration = locked(token);
        var user = users.findById(registration.getUserId()).orElseThrow(RegistrationVerificationService::expired);
        var now = clock.instant();
        if (!user.isEmailVerified()) {
            checkSending(registration, address, now);
            send(user, registration, now);
        }
        return view(user, registration, clock.instant());
    }

    @Transactional
    public PendingRegistrationView correctEmail(String token, String newEmail, String address) {
        var registration = locked(token);
        var user = users.findById(registration.getUserId()).orElseThrow(RegistrationVerificationService::expired);
        var now = clock.instant();
        if (user.isEmailVerified() || user.getEmail().equals(newEmail)) return view(user, registration, clock.instant());
        if (users.existsByEmail(newEmail))
            throw new RegistrationConflictException(Map.of("email", "Email is already registered"));
        checkSending(registration, address, now);
        user.correctEmail(newEmail);
        // Flush uniqueness before delivering; concurrent corrections cannot mail a losing address.
        users.flush();
        send(user, registration, now);
        return view(user, registration, clock.instant());
    }

    private RegistrationVerification authorized(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw expired();
        var registration = registrations.findByTokenHash(digest(token))
                .orElseThrow(RegistrationVerificationService::expired);
        if (!clock.instant().isBefore(registration.getTokenExpiresAt())) throw expired();
        return registration;
    }

    private RegistrationVerification locked(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw expired();
        var userId = registrations.findUserIdByTokenHash(digest(token)).orElseThrow(RegistrationVerificationService::expired);
        users.findForUpdateById(userId).orElseThrow(RegistrationVerificationService::expired);
        // Load after the user lock, so a concurrent code or token replacement cannot be cached.
        var initial = registrations.findById(userId).orElseThrow(RegistrationVerificationService::expired);
        if (!MessageDigest.isEqual(initial.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                digest(token).getBytes(StandardCharsets.US_ASCII))
                || !clock.instant().isBefore(initial.getTokenExpiresAt())) throw expired();
        return initial;
    }

    private void checkSending(RegistrationVerification registration, String address, Instant now) {
        if (now.isBefore(registration.getResendAvailableAt()))
            throw new AuthRateLimitException(Math.max(1,
                    Math.ceilDiv(java.time.Duration.between(now, registration.getResendAvailableAt()).toMillis(), 1000)));
        var limit = properties.emailRegistration();
        var reset = registration.getSendWindowStartedAt().plus(limit.window());
        if (now.isBefore(reset) && registration.getSendsInWindow() >= limit.attempts())
            throw new AuthRateLimitException(Math.max(1,
                    Math.ceilDiv(java.time.Duration.between(now, reset).toMillis(), 1000)));
        limits.checkEmailIp(address);
        limits.checkEmailRegistration(registration.getUserId().toString());
    }

    private void send(User user, RegistrationVerification registration, Instant now) {
        String code;
        do { code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
        while (registration.getCodeHash() != null && passwords.matches(code, registration.getCodeHash()));
        registration.issue(passwords.encode(code), now, properties.emailRegistration().window());
        try {
            email.send(user.getEmail(), code, registration.getCodeExpiresAt());
            registration.delivery(true);
        } catch (MailException exception) {
            // SMTP failures are recoverable state, not a rolled-back account or a logged email body.
            registration.delivery(false);
        }
    }

    private static PendingRegistrationView view(User user, RegistrationVerification registration, Instant now) {
        return new PendingRegistrationView(user.getId(), user.getEmail(), user.isEmailVerified() ? "VERIFIED" : "PENDING",
                now, registration.getCodeExpiresAt(), registration.getResendAvailableAt(),
                5 - registration.getFailedAttempts(), registration.getDeliveryStatus());
    }

    private static VerificationException expired() {
        return new VerificationException(HttpStatus.UNAUTHORIZED, "REGISTRATION_EXPIRED", 0);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
