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
            } else {
                limiter.checkRegistrationIp(request.getRemoteAddr());
            }
        }
        return true;
    }
}
