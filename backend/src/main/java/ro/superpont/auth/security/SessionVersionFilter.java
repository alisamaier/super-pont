package ro.superpont.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ro.superpont.auth.repository.UserRepository;
import tools.jackson.databind.json.JsonMapper;

/** Checks persisted revocation state, including for sessions issued by another app instance. */
public final class SessionVersionFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final JsonMapper json;

    public SessionVersionFilter(UserRepository users, JsonMapper json) {
        this.users = users;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean valid = authentication.getPrincipal() instanceof AccountPrincipal principal
                    && principal.getAccountId() != null
                    && users.findPasswordVersionById(principal.getAccountId())
                            .filter(version -> version == principal.getPasswordVersion()).isPresent();
            if (!valid) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setHeader("Cache-Control", "no-store");
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED, "Session expired. Sign in again"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
