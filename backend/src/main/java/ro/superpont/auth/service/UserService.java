package ro.superpont.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
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
import ro.superpont.auth.security.AccountPrincipal;
import ro.superpont.auth.validation.EmailAddress;
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
        String email = EmailAddress.normalize(request.email());
        rejectRegistrationConflicts(email, request.displayName());
        var user = new User(request.displayName(), email, passwords.encode(request.password()));
        try {
            return transactions.execute(status -> UserView.from(users.saveAndFlush(user)));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException constraint
                    && constraint.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                // The failed insert has rolled back, so the query can see the winning registration.
                rejectRegistrationConflicts(email, request.displayName());
            }
            throw exception;
        }
    }

    private void rejectRegistrationConflicts(String email, String name) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (users.existsByEmail(email)) errors.put("email", "Email is already registered");
        if (users.existsByName(name)) errors.put("displayName", "Display name is already registered");
        if (!errors.isEmpty()) throw new RegistrationConflictException(errors);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        var user = users.findByEmail(EmailAddress.normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        return new AccountPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserView currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserView::from)
                .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Account unavailable"));
    }

    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
