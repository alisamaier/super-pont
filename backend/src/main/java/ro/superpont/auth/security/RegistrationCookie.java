package ro.superpont.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RegistrationCookie {
    public static final String NAME = "SP_REGISTRATION";
    private final boolean secure;
    private final String sameSite;

    public RegistrationCookie(@Value("${server.servlet.session.cookie.secure:true}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public void issue(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, token)
                .httpOnly(true).secure(secure).sameSite(sameSite).path("/api/auth/registration")
                .maxAge(Duration.ofHours(24)).build().toString());
    }
}
