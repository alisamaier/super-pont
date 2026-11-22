package ro.superpont.auth.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class VerificationEmailSender {
    private final AccountEmailSender mail;

    public VerificationEmailSender(AccountEmailSender mail) {
        this.mail = mail;
    }

    public void send(String recipient, String code, Instant expiresAt) {
        var deadline = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'")
                .withZone(ZoneOffset.UTC).format(expiresAt);
        var text = """
                SUPER PONT

                Confirmă adresa de email
                Codul tău de verificare este: %s

                Codul este valabil 10 minute, până la %s, și poate fi folosit o singură dată.
                Ai primit acest mesaj deoarece a fost creat un cont Super Pont cu această adresă.
                Dacă nu ai solicitat înregistrarea, poți ignora acest mesaj.
                Nu comunica acest cod altor persoane.
                """.formatted(code, deadline);
        var html = """
                <!doctype html><html lang="ro"><body style="font-family:Arial,sans-serif;background:#0d131d;color:#edf0f5;padding:32px">
                <div style="max-width:520px;margin:auto;padding:28px;background:#172130;border-radius:12px">
                <strong style="color:#c9f76a;font-size:24px">SUPER PONT</strong>
                <h1 style="font-size:24px">Confirmă adresa de email</h1>
                <p>Introdu acest cod în pagina de confirmare:</p>
                <p style="font-size:36px;letter-spacing:8px;color:#c9f76a"><strong>%s</strong></p>
                <p>Codul este valabil <strong>10 minute</strong>, până la %s, și poate fi folosit o singură dată.</p>
                <p>Ai primit acest mesaj deoarece a fost creat un cont Super Pont cu această adresă.</p>
                <p>Dacă nu ai solicitat înregistrarea, poți ignora acest mesaj. Nu comunica acest cod altor persoane.</p>
                </div></body></html>
                """.formatted(code, deadline);
        mail.send(recipient, "Super Pont — Confirmă adresa de email", text, html);
    }
}
