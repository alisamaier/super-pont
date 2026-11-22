package ro.superpont.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import ro.superpont.auth.dto.LoginRequest;
import ro.superpont.auth.dto.RegisterRequest;
import ro.superpont.auth.dto.UserView;
import ro.superpont.auth.security.AuthRateLimitException;
import ro.superpont.auth.security.AuthRateLimiter;
import ro.superpont.auth.service.RegistrationConflictException;
import ro.superpont.auth.service.UserService;
import ro.superpont.auth.service.RegistrationVerificationService;
import ro.superpont.auth.security.RegistrationCookie;
import ro.superpont.common.DomainException;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserService users;
    private final AuthenticationManager authentication;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final AuthRateLimiter rateLimiter;
    private final RegistrationVerificationService verification;
    private final RegistrationCookie registrationCookie;

    public AuthController(UserService users, AuthenticationManager authentication,
            SessionAuthenticationStrategy sessions, SecurityContextRepository contexts, AuthRateLimiter rateLimiter,
            RegistrationVerificationService verification, RegistrationCookie registrationCookie) {
        this.users = users;
        this.authentication = authentication;
        this.sessions = sessions;
        this.contexts = contexts;
        this.rateLimiter = rateLimiter;
        this.verification = verification;
        this.registrationCookie = registrationCookie;
    }

    @GetMapping("/auth/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registration,
            HttpServletRequest request, HttpServletResponse response) {
        var user = users.register(registration);
        var pending = verification.start(user.id(), request.getRemoteAddr());
        registrationCookie.issue(response, pending.token());
        return ResponseEntity.ok(pending.view());
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ProblemDetail registrationConflict(RegistrationConflictException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setProperty("errors", exception.getErrors());
        return problem;
    }

    @ExceptionHandler(AuthRateLimitException.class)
    public ResponseEntity<ProblemDetail> rateLimited(AuthRateLimitException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfter()))
                .body(problem);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
            HttpServletResponse response) {
        rateLimiter.checkLoginEmail(login.email());
        Authentication user;
        try {
            if (!UserService.fitsBcrypt(login.password())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            user = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
        } catch (DisabledException exception) {
            var pending = verification.resume(login.email(), login.password(), request.getRemoteAddr());
            registrationCookie.issue(response, pending.token());
            return ResponseEntity.accepted().body(pending.view());
        } catch (BadCredentialsException exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        UserView profile = users.currentUser(UUID.fromString(user.getName()));
        sessions.onAuthentication(user, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(user);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/users/me")
    public UserView currentUser(Principal principal) {
        return users.currentUser(UUID.fromString(principal.getName()));
    }
}
