package ro.superpont;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import ro.superpont.auth.service.*;

class PasswordResetEmailSenderTests {
    @Test
    void sendsRomanianHtmlAndPlainTextWithConfiguredOriginFragmentLinkAndExpiry() throws Exception {
        var mail = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(message);
        var sender = new PasswordResetEmailSender(new AccountEmailSender(mail, "Super Pont <no-reply@example.test>"), "https://superpont.example.test");
        String token = "a".repeat(43);
        sender.send("recipient@example.test", token, Instant.parse("2026-11-20T12:30:00Z"));
        message.saveChanges();
        verify(mail).send(message);
        assertThat(message.getSubject()).isEqualTo("Super Pont — Resetează parola");
        assertThat(message.getContentType()).startsWith("multipart/alternative");
        var parts = (Multipart) message.getContent();
        assertThat(parts.getCount()).isEqualTo(2);
        assertThat(parts.getBodyPart(0).getContentType()).startsWith("text/plain");
        assertThat(parts.getBodyPart(1).getContentType()).startsWith("text/html");
        for (int index = 0; index < 2; index++) {
            assertThat(parts.getBodyPart(index).getContent().toString())
                    .contains("SUPER PONT", "https://superpont.example.test/reseteaza-parola#token=" + token,
                            "30 de minute", "20.11.2026 12:30:00 UTC", "Parola ta rămâne neschimbată");
        }
        assertThatThrownBy(() -> new PasswordResetEmailSender(mock(AccountEmailSender.class), "https://host.example/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
