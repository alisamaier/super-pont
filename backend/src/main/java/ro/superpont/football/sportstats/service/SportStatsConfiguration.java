package ro.superpont.football.sportstats.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ro.superpont.football.sportstats.model.SportStatsProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({SportStatsProperties.class, ro.superpont.football.team.model.TeamLogoProperties.class})
public class SportStatsConfiguration {}
