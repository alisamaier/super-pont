package ro.superpont.football.team.model;

import jakarta.persistence.*;

import ro.superpont.common.ExternalEntity;
import ro.superpont.football.country.model.Country;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "teams")
public class Team extends ExternalEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    @Column(name = "logo_hash", length = 64)
    private String logoHash;
    @Column(name = "logo_source_url", length = 2048)
    private String logoSourceUrl;
    @Column(name = "logo_credit", length = 1000)
    private String logoCredit;
    @Column(name = "provider_team_id")
    private Long providerTeamId;
    @Column(name = "logo_checked_at")
    private Instant logoCheckedAt;
    @Column(name = "logo_retry_after")
    private Instant logoRetryAfter;

    public String getLogoHash() { return logoHash; }
    public String getLogoSourceUrl() { return logoSourceUrl; }
    public String getLogoCredit() { return logoCredit; }
    public Long getProviderTeamId() { return providerTeamId; }
    public Instant getLogoRetryAfter() { return logoRetryAfter; }
    public void cacheLogo(String hash, String source, String credit, Long numericId, Instant now) {
        this.logoHash = hash; this.logoSourceUrl = source; this.logoCredit = credit;
        this.providerTeamId = numericId; this.logoCheckedAt = now; this.logoRetryAfter = null;
    }
    public void deferLogo(Instant now, Instant retry) { this.logoCheckedAt = now; this.logoRetryAfter = retry; }

    protected Team() {}

    public Team(Country country, String name, String shortName) {
        this.country = country;
        this.name = name;
        this.shortName = shortName;
    }

    public Country getCountry() {
        return country;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public void updateNames(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }
}
