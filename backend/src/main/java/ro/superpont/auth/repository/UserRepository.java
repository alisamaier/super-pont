package ro.superpont.auth.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ro.superpont.auth.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForUpdateByUsername(String username);

    List<RegistrationMatch> findByUsernameOrEmail(String username, String email);

    interface RegistrationMatch {
        String getUsername();

        String getEmail();
    }
}
