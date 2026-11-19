package ro.superpont.auth.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.superpont.auth.dto.AccountSettings;
import ro.superpont.auth.service.AccountSettingsService;

@RestController
@RequestMapping("/api/users/me/settings")
public class AccountSettingsController {
    private final AccountSettingsService settings;

    public AccountSettingsController(AccountSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public ResponseEntity<AccountSettings> current(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.current(UUID.fromString(principal.getName())));
    }

    @PutMapping
    public ResponseEntity<AccountSettings> update(Principal principal, @Valid @RequestBody AccountSettings request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.update(UUID.fromString(principal.getName()), request));
    }
}
