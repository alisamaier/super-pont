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
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u.id as userId, u.displayName as displayName from User u")
    List<LeaderboardAccount> findLeaderboardAccounts();

    interface LeaderboardAccount {
        UUID getUserId();
        String getDisplayName();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForUpdateById(UUID id);

    @Query("select u.passwordVersion from User u where u.id = :id")
    Optional<Long> findPasswordVersionById(@Param("id") UUID id);
}
