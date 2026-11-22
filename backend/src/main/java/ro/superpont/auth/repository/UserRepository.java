package ro.superpont.auth.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.superpont.auth.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Query("select u.id from User u where u.email = :email")
    Optional<UUID> findIdByEmail(@Param("email") String email);

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from User u where u.displayNameKey = lower(:displayName)")
    boolean existsByName(@Param("displayName") String displayName);

    @Query("select count(u) > 0 from User u where u.displayNameKey = lower(:displayName) and u.id <> :userId")
    boolean existsByNameAndOtherId(@Param("displayName") String displayName, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findForUpdateById(UUID id);

    @Query("select u.passwordVersion from User u where u.id = :id and u.emailVerified = true")
    Optional<Long> findPasswordVersionById(@Param("id") UUID id);
}
