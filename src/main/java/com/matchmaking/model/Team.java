package com.matchmaking.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Team(
        Map<Role, PlayerAssignment> roster,
        int avgMmr,
        int avgEffectiveMmr
) {
    public Team {
        Objects.requireNonNull(roster, "roster must not be null");
        roster = Collections.unmodifiableMap(new EnumMap<>(roster));
    }

    public static Team create(List<PlayerAssignment> assignments) {
        Map<Role, PlayerAssignment> roster = new EnumMap<>(Role.class);
        int totalMmr = 0;
        int totalEffectiveMmr = 0;

        for (PlayerAssignment assignment : assignments) {
            roster.put(assignment.assignedRole(), assignment);
            totalMmr += assignment.player().mmr();
            totalEffectiveMmr += assignment.effectiveMmr();
        }

        int avgMmr = assignments.isEmpty() ? 0 : totalMmr / assignments.size();
        int avgEffectiveMmr = assignments.isEmpty() ? 0 : totalEffectiveMmr / assignments.size();

        return new Team(roster, avgMmr, avgEffectiveMmr);
    }

    public PlayerAssignment getPlayer(Role role) {
        return roster.get(role);
    }

    public int size() {
        return roster.size();
    }
}
