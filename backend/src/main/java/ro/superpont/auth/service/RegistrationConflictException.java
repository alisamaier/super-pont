package ro.superpont.auth.service;

import java.util.Map;

public final class RegistrationConflictException extends AccountConflictException {

    RegistrationConflictException(Map<String, String> errors) {
        super("Registration could not be completed", errors);
    }
}
