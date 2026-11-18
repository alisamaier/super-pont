package ro.superpont.auth.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.superpont.auth.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForUpdateByUsername(String username);

    @Query("select coalesce(u.displayName, u.username) from User u where u.id = :id")
    Optional<String> findDisplayNameById(@Param("id") UUID id);

    List<RegistrationMatch> findByUsernameOrEmail(String username, String email);

    interface RegistrationMatch {
        String getUsername();

        String getEmail();
    }
}
