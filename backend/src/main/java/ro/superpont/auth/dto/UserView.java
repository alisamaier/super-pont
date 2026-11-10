package ro.superpont.auth.dto;

import java.time.Instant;
import java.util.UUID;
import ro.superpont.auth.model.Role;
import ro.superpont.auth.model.User;

public record UserView(UUID id, String username, String email, Role role, Instant createdAt) {
    public static UserView from(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getCreatedAt());
    }
}
