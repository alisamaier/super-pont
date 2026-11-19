package ro.superpont.auth.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
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

    public AccountSettingsService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
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

    @Transactional
    public UserView updateProfile(UUID userId, ProfileRequest request) {
        var user = users.findForUpdateById(userId).orElseThrow(AccountSettingsService::missingAccount);
        user.setDisplayName(request.displayName());
        return UserView.from(user);
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
