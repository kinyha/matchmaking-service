package com.matchmaking.model;

import java.util.Objects;

public record PlayerAssignment(
        Player player,
        Role assignedRole,
        AssignmentType assignmentType,
        int effectiveMmr
) {
    public PlayerAssignment {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(assignedRole, "assignedRole must not be null");
        Objects.requireNonNull(assignmentType, "assignmentType must not be null");
    }

    public static PlayerAssignment create(Player player, Role assignedRole, AssignmentType assignmentType) {
        int effectiveMmr = player.mmr() - assignmentType.getMmrPenalty();
        return new PlayerAssignment(player, assignedRole, assignmentType, effectiveMmr);
    }
}
