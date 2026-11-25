package ro.superpont.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Shared account transport. Callers must never log delivery exceptions or message content. */
@Component
public class AccountEmailSender {
    private final JavaMailSender mail;
    private final String from;
    private final AccountEmailTransport https;

    public AccountEmailSender(JavaMailSender mail, @Value("${app.mail.from}") String from) {
        this.mail = mail;
        this.from = from;
        this.https = null;
    }

    @Autowired
    public AccountEmailSender(JavaMailSender mail, @Value("${app.mail.from}") String from,
            @Value("${app.mail.provider:smtp}") String provider,
            @Value("${app.mail.brevo-api-key:}") String apiKey, JsonMapper json) {
        this.mail = mail;
        this.from = from;
        this.https = switch (provider) {
            case "smtp" -> null;
            case "brevo" -> new BrevoEmailTransport(apiKey, from, json);
            default -> throw new IllegalArgumentException("MAIL_PROVIDER must be smtp or brevo");
        };
    }

    public void send(String recipient, String subject, String text, String html) {
        if (https != null) { https.send(recipient, subject, text, html); return; }
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            var alternatives = new MimeMultipart("alternative");
            var plain = new MimeBodyPart();
            plain.setText(text, "UTF-8");
            var markup = new MimeBodyPart();
            markup.setText(html, "UTF-8", "html");
            alternatives.addBodyPart(plain);
            alternatives.addBodyPart(markup);
            message.setContent(alternatives);
            mail.send(message);
        } catch (MessagingException exception) {
            throw new MailSendException("Account email could not be prepared");
        }
    }
}
