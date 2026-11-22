package ro.superpont.auth.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ro.superpont.auth.model.User;

public final class AccountPrincipal extends org.springframework.security.core.userdetails.User {
    private static final long serialVersionUID = 2L;
    private final UUID accountId;
    private final long passwordVersion;

    public AccountPrincipal(User user) {
        super(user.getId().toString(), user.getPasswordHash(), user.isEmailVerified(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.passwordVersion = user.getPasswordVersion();
        this.accountId = user.getId();
    }

    public long getPasswordVersion() {
        return passwordVersion;
    }

    public UUID getAccountId() { return accountId; }
}
