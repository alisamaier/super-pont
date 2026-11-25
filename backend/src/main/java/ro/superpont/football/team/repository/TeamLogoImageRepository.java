package ro.superpont.football.team.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.superpont.football.team.model.TeamLogoImage;

public interface TeamLogoImageRepository extends JpaRepository<TeamLogoImage, String> {}
