import type { MatchView, TeamView } from "../api/types";
import type { Fixture, Team } from "../types";

const badgeColors = [
  "#215bb7",
  "#a12e49",
  "#7657a6",
  "#277a66",
  "#916421",
  "#3c637e",
];
const dateLabel = new Intl.DateTimeFormat("ro-RO", {
  weekday: "short",
  day: "numeric",
  month: "short",
});
const timeLabel = new Intl.DateTimeFormat("ro-RO", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});
const dayName = new Intl.DateTimeFormat("ro-RO", { weekday: "long" });
const shortDay = new Intl.DateTimeFormat("ro-RO", { weekday: "short" });
const monthDay = new Intl.DateTimeFormat("ro-RO", {
  day: "numeric",
  month: "short",
});

function badge(team: TeamView): Team {
  const colorIndex =
    Array.from(team.id).reduce(
      (hash, char) => (hash * 31 + char.charCodeAt(0)) >>> 0,
      0,
    ) % badgeColors.length;
  return {
    ...team,
    initials: (team.shortName.trim() || team.name)
      .slice(0, 4)
      .toLocaleUpperCase("ro-RO"),
    color: badgeColors[colorIndex],
    accent: "#f0f3f7",
  };
}

export function toFixture(match: MatchView): Fixture {
  const kickoff = new Date(match.kickoff);
  return {
    id: match.id,
    kickoff: match.kickoff,
    home: badge(match.homeTeam),
    away: badge(match.awayTeam),
    day: [
      kickoff.getFullYear(),
      String(kickoff.getMonth() + 1).padStart(2, "0"),
      String(kickoff.getDate()).padStart(2, "0"),
    ].join("-"),
    date: dateLabel.format(kickoff).toLocaleUpperCase("ro-RO"),
    time: timeLabel.format(kickoff),
    venue: match.venue?.trim() || "Stadion neanunțat",
    status: match.status,
    homeScore: match.homeScore,
    awayScore: match.awayScore,
  };
}

export function fixtureDays(fixtures: readonly Fixture[]) {
  return [
    ...new Map(
      fixtures.map((fixture) => {
        const date = new Date(fixture.kickoff);
        return [
          fixture.day,
          {
            id: fixture.day,
            label: dayName.format(date),
            short: shortDay.format(date),
            date: monthDay.format(date),
          },
        ] as const;
      }),
    ).values(),
  ];
}
