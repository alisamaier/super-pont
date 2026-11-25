package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import ro.superpont.auth.service.VerificationEmailSender;

class VerificationEmailSenderTests {
    @Test
    void producesRomanianHtmlAndPlainTextAlternativesIncludingLeadingZerosAndExpiry() throws Exception {
        var mail = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(mail.createMimeMessage()).thenReturn(message);
        var sender = new VerificationEmailSender(new ro.superpont.auth.service.AccountEmailSender(mail, "Super Pont <no-reply@example.test>"), "https://frontend.example.test");
        sender.send("recipient@example.test", "001234", Instant.parse("2026-11-19T12:10:00Z"));
        message.saveChanges();
        verify(mail).send(message);
        assertThat(((Multipart) message.getContent()).getBodyPart(0).getContent().toString()).contains("https://frontend.example.test/confirma-email");
        assertThat(message.getSubject()).contains("Super Pont", "Confirmă");
        assertThat(message.getContentType()).startsWith("multipart/alternative");
        var alternatives = (Multipart) message.getContent();
        assertThat(alternatives.getCount()).isEqualTo(2);
        assertThat(alternatives.getBodyPart(0).getContentType()).startsWith("text/plain");
        assertThat(alternatives.getBodyPart(1).getContentType()).startsWith("text/html");
        for (int index = 0; index < 2; index++)
            assertThat(alternatives.getBodyPart(index).getContent().toString())
                    .contains("001234", "10 minute", "19.11.2026 12:10:00 UTC", "Ai primit acest mesaj");
    }
}
