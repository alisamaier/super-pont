package ro.superpont.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ro.superpont.auth.dto.*;
import ro.superpont.auth.security.AuthRateLimitException;
import ro.superpont.auth.service.PasswordResetService;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService resets;
    public PasswordResetController(PasswordResetService resets) { this.resets = resets; }
    public record Message(String message) {}

    @PostMapping("/request")
    public ResponseEntity<Message> request(@Valid @RequestBody PasswordResetEmailRequest input) {
        resets.request(input.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Message(PasswordResetService.CONFIRMATION));
    }

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@Valid @RequestBody PasswordResetTokenRequest input) {
        resets.validate(input.token());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        resets.reset(input.token(), input.newPassword());
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(AuthRateLimitException.class)
    public ResponseEntity<ProblemDetail> limited(AuthRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfter()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
    }
}
