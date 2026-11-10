package ro.superpont.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.superpont.auth.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    List<RegistrationMatch> findByUsernameOrEmail(String username, String email);

    interface RegistrationMatch {
        String getUsername();

        String getEmail();
    }
}
