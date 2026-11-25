package ro.superpont.football.sportstats.service;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import ro.superpont.football.match.model.MatchStatus;
import ro.superpont.football.sportstats.client.SportStatsException;
import ro.superpont.football.sportstats.model.SportStatsProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class SportStatsMatchMapper {
    enum Stage {
        REGULAR("Sezon regulat", 100), PLAY_OFF("Play-off", 200), PLAY_OUT("Play-out", 300);
        final String label;
        final int position;
        Stage(String label, int position) { this.label = label; this.position = position; }
    }
    record Side(String code, String name, String shortName) {}
    record Planned(String code, String league, String division, String season, LocalDate start, LocalDate end,
            Stage stage, int round, Side home, Side away, OffsetDateTime kickoff, String venue,
            MatchStatus status, Integer homeScore, Integer awayScore, Instant updatedAt, String warning) {}
    private final SportStatsProperties properties;
    private final Map<String, Stage> divisions = new HashMap<>();
    private static final Pattern ROUND = Pattern.compile("(?i)^(Week|Round|Regular Season|Championship(?: (?:Group|Round))?|Play[- ]?offs?|Relegation(?: (?:Group|Round))?|Play[- ]?outs?)\\s*[-:]?\\s*(\\d+)$");

    SportStatsMatchMapper(SportStatsProperties properties, JsonMapper json) {
        this.properties = properties;
        try {
            for (String entry : properties.phaseDivisions().split(",")) {
                var pair = entry.strip().split("=", 2);
                if (pair.length != 2 || !pair[0].matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException();
                divisions.put(pair[0], Stage.valueOf(pair[1].strip()));
            }
        } catch (Exception exception) { throw new IllegalArgumentException("SPORTSTATS_PHASE_DIVISIONS must be division=REGULAR,division=PLAY_OFF,division=PLAY_OUT"); }
    }

    String matchCode(JsonNode row) { return code(row, "code"); }

    Planned map(JsonNode row, OffsetDateTime lastKnownKickoff) {
        if (!"Romania: Superliga".equals(text(row, "league_name", 120)) || !"l60".equals(code(row, "league_code")))
            throw invalid("WRONG_COMPETITION");
        String season = text(row, "season", 50);
        if (!season.matches("20\\d{2}/20\\d{2}")) throw invalid("INVALID_SEASON");
        int year = Integer.parseInt(season.substring(0, 4)), next = Integer.parseInt(season.substring(5));
        if (next != year + 1) throw invalid("INVALID_SEASON");
        LocalDate start = LocalDate.of(year, properties.seasonStartMonth(), properties.seasonStartDay());
        LocalDate end = LocalDate.of(next, properties.seasonStartMonth(), properties.seasonStartDay()).minusDays(1);
        OffsetDateTime kickoff;
        Instant updatedAt = null;
        try {
            kickoff = row.path("start_at").isNull() || row.path("start_at").isMissingNode()
                    ? lastKnownKickoff : OffsetDateTime.parse(text(row, "start_at", 64)).withOffsetSameInstant(ZoneOffset.UTC);
            if (kickoff == null) throw invalid("KICKOFF_UNAVAILABLE");
            if (!row.path("updated_at").isMissingNode() && !row.path("updated_at").isNull())
                updatedAt = Instant.parse(text(row, "updated_at", 64));
        } catch (Exception exception) { throw invalid("INVALID_KICKOFF_OR_TIMESTAMP"); }
        if (kickoff.toLocalDate().isBefore(start) || kickoff.toLocalDate().isAfter(end)) throw invalid("KICKOFF_OUTSIDE_SEASON");
        String division = code(row, "division_code");
        if (division.length() > 32) throw invalid("INVALID_DIVISION_CODE");
        var round = ROUND.matcher(text(row, "round", 100));
        if (!round.matches()) throw invalid("UNKNOWN_ROUND_FORMAT");
        int number;
        try { number = Integer.parseInt(round.group(2)); } catch (Exception exception) { throw invalid("INVALID_ROUND"); }
        if (number <= 0 || number > 1000) throw invalid("INVALID_ROUND");
        String label = round.group(1).toLowerCase(Locale.ROOT);
        Stage stage = label.startsWith("championship") || label.matches("play[- ]?offs?") ? Stage.PLAY_OFF
                : label.startsWith("relegation") || label.matches("play[- ]?outs?") ? Stage.PLAY_OUT
                : label.startsWith("regular") ? Stage.REGULAR : divisions.get(division);
        if (stage == null) throw invalid("PHASE_MAPPING_REQUIRED");
        var home = side(row.path("home_team"));
        var away = side(row.path("away_team"));
        if (home.code().equals(away.code())) throw invalid("IDENTICAL_TEAMS");
        MatchStatus status = status(row);
        String warning = status == MatchStatus.UNKNOWN ? "UNKNOWN_STATUS" : null;
        Integer homeScore = null, awayScore = null;
        if (status == MatchStatus.FINISHED || status == MatchStatus.LIVE) {
            homeScore = score(row.path("home_team").path("score"));
            awayScore = score(row.path("away_team").path("score"));
            if (homeScore == null || awayScore == null) {
                homeScore = null; awayScore = null;
                if (status == MatchStatus.FINISHED) { status = MatchStatus.UNKNOWN; warning = "FINAL_SCORE_MISSING"; }
            }
        }
        String venue = row.path("venue").isTextual() ? row.path("venue").asText().strip() : null;
        if (venue != null && venue.length() > 160) throw invalid("INVALID_VENUE");
        return new Planned(code(row, "code"), "l60", division, season, start, end, stage, number, home, away,
                kickoff, venue, status, homeScore, awayScore, updatedAt, warning);
    }

    private static MatchStatus status(JsonNode row) {
        String normalized = row.path("status_code").asText("").strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            String raw = row.path("status").asText("").strip().toLowerCase(Locale.ROOT);
            if (raw.matches("\\d{1,3}(?:\\+\\d{1,2})?") || Set.of("1h", "2h", "ht").contains(raw)) return MatchStatus.LIVE;
            normalized = switch (raw) { case "ns" -> "scheduled"; case "ft" -> "final"; default -> raw; };
        }
        // Canonical vocabulary from the official OpenAPI schema. Unknown values never become finals.
        return switch (normalized) {
            case "scheduled" -> MatchStatus.SCHEDULED;
            case "live", "halftime", "intermission" -> MatchStatus.LIVE;
            case "final", "final_overtime", "final_penalties" -> MatchStatus.FINISHED;
            case "postponed", "delayed" -> MatchStatus.POSTPONED;
            case "cancelled" -> MatchStatus.CANCELLED;
            case "suspended", "abandoned" -> MatchStatus.SUSPENDED;
            default -> MatchStatus.UNKNOWN;
        };
    }
    private static Integer score(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 0 ? value.asInt() : null;
    }
    private static Side side(JsonNode node) {
        String name = node.path("display_name").isTextual() && !node.path("display_name").asText().isBlank()
                ? text(node, "display_name", 120) : text(node, "name", 120);
        String shortName = name.substring(0, Math.min(name.length(), 50));
        if (Character.isHighSurrogate(shortName.charAt(shortName.length() - 1))) shortName = shortName.substring(0, shortName.length() - 1);
        return new Side(code(node, "code"), name, shortName);
    }
    private static String code(JsonNode node, String field) {
        String value = text(node, field, 64);
        if (!value.matches("[A-Za-z0-9_-]+")) throw invalid("INVALID_EXTERNAL_ID");
        return value;
    }
    private static String text(JsonNode node, String field, int limit) {
        var value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().strip().length() > limit) throw invalid("INVALID_MATCH_FIELD");
        return value.asText().strip();
    }
    private static SportStatsException invalid(String code) { return new SportStatsException(code); }
}
