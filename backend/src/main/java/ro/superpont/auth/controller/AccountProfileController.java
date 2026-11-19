package ro.superpont.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.superpont.auth.dto.PasswordChangeRequest;
import ro.superpont.auth.dto.ProfileRequest;
import ro.superpont.auth.dto.UserView;
import ro.superpont.auth.service.AccountSettingsService;

@RestController
@RequestMapping("/api/users/me")
public class AccountProfileController {
    private final AccountSettingsService settings;

    public AccountProfileController(AccountSettingsService settings) {
        this.settings = settings;
    }

    @PutMapping("/profile")
    public ResponseEntity<UserView> updateProfile(Principal principal, @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.updateProfile(UUID.fromString(principal.getName()), request));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        settings.changePassword(UUID.fromString(authentication.getName()), body);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
