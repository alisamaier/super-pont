package ro.superpont.auth.service;

import java.util.UUID;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import ro.superpont.auth.dto.AccountSettings;
import ro.superpont.auth.dto.PasswordChangeRequest;
import ro.superpont.auth.dto.ProfileRequest;
import ro.superpont.auth.dto.UserView;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.common.DomainException;

@Service
@Transactional(readOnly = true)
public class AccountSettingsService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;

    public AccountSettingsService(UserRepository users, PasswordEncoder passwords, TransactionTemplate transactions) {
        this.users = users;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    public AccountSettings current(UUID userId) {
        var user = users.findById(userId).orElseThrow(AccountSettingsService::missingAccount);
        return new AccountSettings(user.isHidePredictionsUntilKickoff());
    }

    @Transactional
    public AccountSettings update(UUID userId, AccountSettings settings) {
        var user = users.findForUpdateById(userId).orElseThrow(AccountSettingsService::missingAccount);
        user.setHidePredictionsUntilKickoff(settings.hidePredictionsUntilKickoff());
        return new AccountSettings(user.isHidePredictionsUntilKickoff());
    }

    private static DomainException missingAccount() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "Account unavailable");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserView updateProfile(UUID userId, ProfileRequest request) {
        try {
            return transactions.execute(status -> {
                var user = users.findForUpdateById(userId).orElseThrow(AccountSettingsService::missingAccount);
                if (users.existsByNameAndOtherId(request.displayName(), userId)) throw nameTaken();
                user.setDisplayName(request.displayName());
                users.flush();
                return UserView.from(user);
            });
        } catch (DataIntegrityViolationException exception) {
            // The failed write has rolled back before returning the same field error as the precheck.
            if (exception.getCause() instanceof ConstraintViolationException constraint
                    && constraint.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) throw nameTaken();
            throw exception;
        }
    }

    private static AccountConflictException nameTaken() {
        return new AccountConflictException(Map.of("displayName", "Display name is already registered"));
    }

    @Transactional
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        if (!UserService.fitsBcrypt(request.newPassword())) {
            throw DomainException.invalid("Password must be at most 72 UTF-8 bytes");
        }
        var user = users.findForUpdateById(userId).orElseThrow(AccountSettingsService::missingAccount);
        if (!UserService.fitsBcrypt(request.currentPassword()) || !passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw DomainException.invalid("Current password is incorrect");
        }
        user.changePassword(passwords.encode(request.newPassword()));
    }
}
