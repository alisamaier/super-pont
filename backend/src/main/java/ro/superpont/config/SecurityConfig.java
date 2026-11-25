package ro.superpont.config;

import static org.springframework.security.config.Customizer.withDefaults;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.CorsFilter;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.auth.security.SessionVersionFilter;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {
    private static final String[] FOOTBALL_PATHS = {
        "/api/countries/**", "/api/competitions/**", "/api/teams/**", "/api/seasons/**",
        "/api/phases/**", "/api/rounds/**", "/api/matches/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            CsrfTokenRepository csrfTokens, JsonMapper json, UserRepository users) throws Exception {
        return http
                .cors(withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .securityContext(context -> context.securityContextRepository(contexts))
                .addFilterAfter(new SessionVersionFilter(users, json), CorsFilter.class)
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problem(response, json, HttpStatus.UNAUTHORIZED, "Sign in required"))
                        .accessDeniedHandler((request, response, exception) ->
                                problem(response, json, HttpStatus.FORBIDDEN, "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/sportstats/sync", "/api/admin/sportstats/logos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/auth/password-reset/request",
                                "/api/auth/password-reset/validate", "/api/auth/password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/registration").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registration/confirm",
                                "/api/auth/registration/resend", "/api/auth/registration/email").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/me", "/api/predictions/me").authenticated()
                        .requestMatchers(HttpMethod.HEAD, "/api/predictions/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/me/settings").authenticated()
                        .requestMatchers(HttpMethod.HEAD, "/api/users/me/settings").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/me/settings").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/me/profile", "/api/users/me/password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/leaderboard", "/api/leaderboard/users/{userId}/predictions", "/api/leaderboard/superliga",
                                "/api/leaderboard/superliga/users/{userId}/predictions").authenticated()
                        .requestMatchers(HttpMethod.HEAD, "/api/leaderboard", "/api/leaderboard/users/{userId}/predictions", "/api/leaderboard/superliga",
                                "/api/leaderboard/superliga/users/{userId}/predictions").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/matches/{matchId}/prediction").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/matches/{matchId}/prediction").authenticated()
                        .requestMatchers(HttpMethod.GET, FOOTBALL_PATHS).permitAll()
                        .requestMatchers(HttpMethod.HEAD, FOOTBALL_PATHS).permitAll()
                        .requestMatchers(FOOTBALL_PATHS).hasRole("ADMIN")
                        .anyRequest().denyAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    private static void problem(HttpServletResponse response, JsonMapper json,
            HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
