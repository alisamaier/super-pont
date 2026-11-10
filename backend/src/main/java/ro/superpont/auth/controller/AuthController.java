package ro.superpont.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
import ro.superpont.common.DomainException;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserService users;
    private final AuthenticationManager authentication;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final AuthRateLimiter rateLimiter;

    public AuthController(UserService users, AuthenticationManager authentication,
            SessionAuthenticationStrategy sessions, SecurityContextRepository contexts, AuthRateLimiter rateLimiter) {
        this.users = users;
        this.authentication = authentication;
        this.sessions = sessions;
        this.contexts = contexts;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/auth/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/auth/register")
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        return users.register(request);
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
    public UserView login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
            HttpServletResponse response) {
        rateLimiter.checkLoginUsername(login.username());
        Authentication user;
        try {
            if (!UserService.fitsBcrypt(login.password())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            user = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        } catch (BadCredentialsException exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        UserView profile = users.currentUser(user.getName());
        sessions.onAuthentication(user, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(user);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return profile;
    }

    @GetMapping("/users/me")
    public UserView currentUser(Principal principal) {
        return users.currentUser(principal.getName());
    }
}
