package ro.superpont.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public final class AuthIpRateLimitInterceptor implements HandlerInterceptor {
    private final AuthRateLimiter limiter;

    public AuthIpRateLimitInterceptor(AuthRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.POST.matches(request.getMethod())) {
            // Tomcat resolves the peer address and applies the explicit proxy allowlist.
            if ("/api/auth/login".equals(request.getServletPath())) {
                limiter.checkLoginIp(request.getRemoteAddr());
            } else if ("/api/auth/register".equals(request.getServletPath())) {
                limiter.checkRegistrationIp(request.getRemoteAddr());
            } else if ("/api/auth/password-reset/request".equals(request.getServletPath())) {
                limiter.checkPasswordResetIp(request.getRemoteAddr());
            } else if (request.getServletPath().startsWith("/api/auth/password-reset/")) {
                limiter.checkPasswordResetSubmissionIp(request.getRemoteAddr());
            } else {
                limiter.checkVerificationIp(request.getRemoteAddr());
            }
        }
        return true;
    }
}
