package ro.superpont.auth.service;

import java.util.Map;
import org.springframework.http.HttpStatus;
import ro.superpont.common.DomainException;

public class AccountConflictException extends DomainException {
    private final Map<String, String> errors;

    public AccountConflictException(Map<String, String> errors) { this("Account details conflict", errors); }

    protected AccountConflictException(String message, Map<String, String> errors) {
        super(HttpStatus.CONFLICT, message);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() { return errors; }
}
