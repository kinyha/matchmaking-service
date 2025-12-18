package com.matchmaking.model;

import java.util.Objects;

public record Player(
        String id,
        String displayName,
        int mmr,
        Rank rank,
        Role primaryRole,
        Role secondaryRole
) {
    public Player {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(primaryRole, "primaryRole must not be null");
        Objects.requireNonNull(secondaryRole, "secondaryRole must not be null");
        if (mmr < 0) {
            throw new IllegalArgumentException("mmr must be non-negative");
        }
        if (primaryRole == secondaryRole) {
            throw new IllegalArgumentException("primaryRole and secondaryRole must be different");
        }
    }

    public static Player create(String id, String displayName, int mmr, Role primaryRole, Role secondaryRole) {
        return new Player(id, displayName, mmr, Rank.fromMmr(mmr), primaryRole, secondaryRole);
    }
}
