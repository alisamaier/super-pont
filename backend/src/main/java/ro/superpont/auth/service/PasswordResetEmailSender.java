package ro.superpont.auth.service;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class PasswordResetEmailSender {
    private final AccountEmailSender mail;
    private final String frontendUrl;

    public PasswordResetEmailSender(AccountEmailSender mail,
            @Value("${app.password-reset.frontend-url}") String frontendUrl) {
        var origin = URI.create(frontendUrl);
        if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                || origin.getHost() == null || origin.getUserInfo() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty())
                || origin.getQuery() != null || origin.getFragment() != null) {
            throw new IllegalArgumentException("PASSWORD_RESET_FRONTEND_URL must be an HTTP(S) origin without a trailing slash or path");
        }
        this.mail = mail;
        this.frontendUrl = frontendUrl;
    }

    public void send(String recipient, String token, Instant expiresAt) {
        // Fragments are not sent in HTTP requests or Referer headers.
        String link = frontendUrl + "/reseteaza-parola#token=" + token;
        String deadline = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'")
                .withZone(ZoneOffset.UTC).format(expiresAt);
        String text = """
                SUPER PONT

                Resetează parola
                Ai solicitat resetarea parolei contului tău Super Pont.
                Alege o parolă nouă folosind acest link:
                %s

                Linkul este valabil 30 de minute, până la %s, și poate fi folosit o singură dată.
                Dacă nu ai solicitat resetarea, ignoră acest email. Parola ta rămâne neschimbată.
                Nu comunica acest link altor persoane.
                """.formatted(link, deadline);
        String html = """
                <!doctype html><html lang="ro"><body style="font-family:Arial,sans-serif;background:#0d131d;color:#edf0f5;padding:32px">
                <div style="max-width:520px;margin:auto;padding:28px;background:#172130;border-radius:12px">
                <strong style="color:#c9f76a;font-size:24px">SUPER PONT</strong>
                <h1 style="font-size:24px">Resetează parola</h1>
                <p>Ai solicitat resetarea parolei contului tău Super Pont.</p>
                <p><a href="%s" style="color:#c9f76a">Alege o parolă nouă</a></p>
                <p>Linkul este valabil <strong>30 de minute</strong>, până la %s, și poate fi folosit o singură dată.</p>
                <p>Dacă nu ai solicitat resetarea, ignoră acest email. Parola ta rămâne neschimbată.</p>
                <p>Nu comunica acest link altor persoane.</p>
                </div></body></html>
                """.formatted(HtmlUtils.htmlEscape(link), deadline);
        mail.send(recipient, "Super Pont — Resetează parola", text, html);
    }
}
