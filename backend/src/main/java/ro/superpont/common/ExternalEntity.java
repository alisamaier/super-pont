package ro.superpont.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class ExternalEntity extends BaseEntity {
    @Column(nullable = false, length = 32) private String provider = "LOCAL";
    @Column(name = "external_id", length = 256) private String externalId;

    public String getProvider() { return provider; }
    public String getExternalId() { return externalId; }
    public void identifyExternal(String provider, String id) {
        if (externalId != null && (!this.provider.equals(provider) || !externalId.equals(id)))
            throw new IllegalStateException("External identity cannot be replaced");
        this.provider = provider;
        this.externalId = id;
    }
}
