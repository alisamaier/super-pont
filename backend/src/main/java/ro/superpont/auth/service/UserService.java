package ro.superpont.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ro.superpont.auth.dto.RegisterRequest;
import ro.superpont.auth.dto.UserView;
import ro.superpont.auth.model.User;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.common.DomainException;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;

    public UserService(UserRepository users, PasswordEncoder passwords, TransactionTemplate transactions) {
        this.users = users;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    public UserView register(RegisterRequest request) {
        if (!fitsBcrypt(request.password())) {
            throw DomainException.invalid("Password must be at most 72 UTF-8 bytes");
        }
        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().toLowerCase(Locale.ROOT);
        rejectRegistrationConflicts(username, email);
        var user = new User(username, email, passwords.encode(request.password()));
        try {
            return transactions.execute(status -> UserView.from(users.saveAndFlush(user)));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException constraint
                    && constraint.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                // The failed insert has rolled back, so the query can see the winning registration.
                rejectRegistrationConflicts(username, email);
            }
            throw exception;
        }
    }

    private void rejectRegistrationConflicts(String username, String email) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (var match : users.findByUsernameOrEmail(username, email)) {
            if (username.equals(match.getUsername())) {
                errors.put("username", "Username is already taken");
            }
            if (email.equals(match.getEmail())) {
                errors.put("email", "Email is already registered");
            }
        }
        if (!errors.isEmpty()) {
            throw new RegistrationConflictException(errors);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var user = users.findByUsername(username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public UserView currentUser(String username) {
        return users.findByUsername(username)
                .map(UserView::from)
                .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Account unavailable"));
    }

    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
