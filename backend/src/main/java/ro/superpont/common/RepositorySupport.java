package ro.superpont.common;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public final class RepositorySupport {
    private RepositorySupport() {}

    public static <T> T require(JpaRepository<T, UUID> repository, UUID id, String name) {
        return repository.findById(id).orElseThrow(() -> DomainException.missing(name));
    }

    public static Pageable page(int page, int size, String property) {
        if (page < 0 || size < 1 || size > 100)
            throw DomainException.invalid(
                    "Page must be nonnegative and size must be between 1 and 100");
        return PageRequest.of(page, size, Sort.by(property).and(Sort.by("id")));
    }
}
