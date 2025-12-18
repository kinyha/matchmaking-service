package com.matchmaking.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Match(
        String id,
        Team team1,
        Team team2,
        int avgMmr,
        int mmrDifference,
        Instant createdAt
) {
    public Match {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(team1, "team1 must not be null");
        Objects.requireNonNull(team2, "team2 must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Match create(Team team1, Team team2, Instant createdAt) {
        String id = UUID.randomUUID().toString();
        int avgMmr = (team1.avgMmr() + team2.avgMmr()) / 2;
        int mmrDifference = Math.abs(team1.avgEffectiveMmr() - team2.avgEffectiveMmr());
        return new Match(id, team1, team2, avgMmr, mmrDifference, createdAt);
    }
}
