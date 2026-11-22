package ro.superpont.auth.service;

import org.springframework.http.HttpStatus;
import ro.superpont.common.DomainException;

public class VerificationException extends DomainException {
    private final String code;
    private final int attemptsRemaining;

    public VerificationException(HttpStatus status, String code, int attemptsRemaining) {
        super(status, code);
        this.code = code;
        this.attemptsRemaining = attemptsRemaining;
    }

    public String getCode() { return code; }
    public int getAttemptsRemaining() { return attemptsRemaining; }
}
