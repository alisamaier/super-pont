import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { TeamBadge } from "./TeamBadge";
import { teamLogoUrl } from "../lib/teamLogos";
import type { Team } from "../types";

const team: Team = { id: "00000000-0000-0000-0000-000000000001", name: "FCSB", shortName: "FCSB",
  initials: "FCSB", color: "red", accent: "blue", logoUrl: `/api/teams/00000000-0000-0000-0000-000000000001/logo?v=${"a".repeat(64)}`,
  logoCredit: "Crest: Autor, CC BY-SA 4.0" };

describe("TeamBadge", () => {
  afterEach(cleanup);
  it("uses only our backend, preserves dimensions and displays supplied credit", () => {
    render(<TeamBadge team={team} />);
    const image = screen.getByRole("img", { name: "Sigla FCSB" });
    expect(image.getAttribute("src")).toContain(team.logoUrl);
    expect(image.getAttribute("width")).toBe("56");
    expect(image.getAttribute("height")).toBe("64");
    expect(screen.getByText(team.logoCredit!)).not.toBeNull();
  });
  it("falls back after an image error and retries when the cached version changes", () => {
    const view = render(<TeamBadge team={team} />);
    fireEvent.error(screen.getByRole("img"));
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.getByText("FCSB")).not.toBeNull();
    view.rerender(<TeamBadge team={{ ...team, logoUrl: team.logoUrl!.replace(/a{64}/, "b".repeat(64)) }} />);
    expect(screen.getByRole("img")).not.toBeNull();
  });
  it("keeps the original symbol when absent and rejects external or another team's URLs", () => {
    render(<TeamBadge team={{ ...team, logoUrl: null }} />);
    expect(screen.queryByRole("img")).toBeNull();
    expect(teamLogoUrl({ ...team, logoUrl: "https://img.sportstatsapi.com/logo.png" })).toBeNull();
    expect(teamLogoUrl({ ...team, id: "another-team" })).toBeNull();
    expect(teamLogoUrl({ ...team, logoUrl: team.logoUrl + "&key=secret" })).toBeNull();
  });
});
