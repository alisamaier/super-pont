package ro.superpont.football.team.model;

import jakarta.persistence.*;

@Entity
@Table(name = "team_logo_images")
public class TeamLogoImage {
    @Id
    @Column(length = 64, nullable = false)
    private String hash;
    // bytea, not a PostgreSQL large-object OID: no filesystem or LOB transaction dependency.
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] png;
    protected TeamLogoImage() {}
    public TeamLogoImage(String hash, byte[] png) { this.hash = hash; this.png = png.clone(); }
    public byte[] getPng() { return png.clone(); }
}
