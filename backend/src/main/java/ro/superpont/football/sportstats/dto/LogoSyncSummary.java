package ro.superpont.football.sportstats.dto;

import java.util.Set;

public record LogoSyncSummary(int downloaded, int cached, int missing, int errors, Set<String> warnings) {}
