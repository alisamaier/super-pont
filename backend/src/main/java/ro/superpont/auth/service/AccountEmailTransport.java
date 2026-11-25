package ro.superpont.auth.service;

public interface AccountEmailTransport {
    void send(String recipient, String subject, String text, String html);
}
