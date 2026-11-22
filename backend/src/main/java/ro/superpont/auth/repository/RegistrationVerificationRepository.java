package ro.superpont.auth.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ro.superpont.auth.model.RegistrationVerification;

public interface RegistrationVerificationRepository extends JpaRepository<RegistrationVerification, UUID> {
    Optional<RegistrationVerification> findByTokenHash(String tokenHash);

    @Query("select r.userId from RegistrationVerification r where r.tokenHash = :hash")
    Optional<UUID> findUserIdByTokenHash(@Param("hash") String hash);
}
