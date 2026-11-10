package ro.superpont.auth.service;

import java.util.Map;
import org.springframework.http.HttpStatus;
import ro.superpont.common.DomainException;

public final class RegistrationConflictException extends DomainException {
    private final Map<String, String> errors;

    RegistrationConflictException(Map<String, String> errors) {
        super(HttpStatus.CONFLICT, "Registration could not be completed");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
