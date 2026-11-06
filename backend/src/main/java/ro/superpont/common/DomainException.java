package ro.superpont.common;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {
    private final HttpStatus status;

    public DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, message);
    }

    public static DomainException missing(String name) {
        return new DomainException(HttpStatus.NOT_FOUND, name + " not found");
    }
}
