package ro.superpont.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ro.superpont.auth.dto.*;
import ro.superpont.auth.security.AuthRateLimitException;
import ro.superpont.auth.security.RegistrationCookie;
import ro.superpont.auth.service.RegistrationConflictException;
import ro.superpont.auth.service.RegistrationVerificationService;
import ro.superpont.auth.service.VerificationException;

@RestController
@RequestMapping("/api/auth/registration")
public class RegistrationController {
    private final RegistrationVerificationService verification;

    public RegistrationController(RegistrationVerificationService verification) { this.verification = verification; }

    @GetMapping
    public PendingRegistrationView status(@CookieValue(name = RegistrationCookie.NAME, required = false) String token) {
        return verification.status(token);
    }

    @PostMapping("/confirm")
    public PendingRegistrationView confirm(@CookieValue(name = RegistrationCookie.NAME, required = false) String token,
            @Valid @RequestBody ConfirmRegistrationRequest request) {
        return verification.confirm(token, request.code());
    }

    @PostMapping("/resend")
    public PendingRegistrationView resend(@CookieValue(name = RegistrationCookie.NAME, required = false) String token,
            HttpServletRequest request) {
        return verification.resend(token, request.getRemoteAddr());
    }

    @PostMapping("/email")
    public PendingRegistrationView correctEmail(@CookieValue(name = RegistrationCookie.NAME, required = false) String token,
            @Valid @RequestBody CorrectRegistrationEmailRequest email, HttpServletRequest request) {
        return verification.correctEmail(token, email.email(), request.getRemoteAddr());
    }

    @ExceptionHandler(VerificationException.class)
    public ProblemDetail verification(VerificationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setProperty("code", exception.getCode());
        problem.setProperty("attemptsRemaining", exception.getAttemptsRemaining());
        return problem;
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ProblemDetail conflict(RegistrationConflictException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setProperty("errors", exception.getErrors());
        return problem;
    }

    @ExceptionHandler(AuthRateLimitException.class)
    public ResponseEntity<ProblemDetail> limited(AuthRateLimitException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setProperty("code", "RATE_LIMITED");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfter())).body(problem);
    }
}
