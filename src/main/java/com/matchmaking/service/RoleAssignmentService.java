package com.matchmaking.service;

import com.matchmaking.model.AssignmentType;
import com.matchmaking.model.Player;
import com.matchmaking.model.PlayerAssignment;
import com.matchmaking.model.Role;

import java.util.*;

public class RoleAssignmentService {
    private static final int PLAYERS_PER_ROLE = 2;

    public Optional<List<PlayerAssignment>> assignRoles(List<Player> players) {
        if (players.size() != 10) {
            return Optional.empty();
        }

        Map<Role, List<PlayerAssignment>> roleAssignments = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            roleAssignments.put(role, new ArrayList<>());
        }

        Set<Player> unassigned = new HashSet<>(players);
        List<PlayerAssignment> assignments = new ArrayList<>();

        // Pass 1: Assign PRIMARY roles
        assignByPreference(unassigned, roleAssignments, assignments, AssignmentType.PRIMARY,
                Player::primaryRole);

        // Pass 2: Assign SECONDARY roles
        assignByPreference(unassigned, roleAssignments, assignments, AssignmentType.SECONDARY,
                Player::secondaryRole);

        // Pass 3: AUTOFILL remaining players
        assignAutofill(unassigned, roleAssignments, assignments);

        // Validate: each role must have exactly 2 players
        for (Role role : Role.values()) {
            if (roleAssignments.get(role).size() != PLAYERS_PER_ROLE) {
                return Optional.empty();
            }
        }

        return Optional.of(assignments);
    }

    private void assignByPreference(
            Set<Player> unassigned,
            Map<Role, List<PlayerAssignment>> roleAssignments,
            List<PlayerAssignment> assignments,
            AssignmentType type,
            java.util.function.Function<Player, Role> roleExtractor) {

        List<Player> toAssign = new ArrayList<>(unassigned);
        toAssign.sort(Comparator.comparingInt(Player::mmr).reversed());

        for (Player player : toAssign) {
            if (!unassigned.contains(player)) {
                continue;
            }

            Role preferredRole = roleExtractor.apply(player);
            List<PlayerAssignment> roleList = roleAssignments.get(preferredRole);

            if (roleList.size() < PLAYERS_PER_ROLE) {
                PlayerAssignment assignment = PlayerAssignment.create(player, preferredRole, type);
                roleList.add(assignment);
                assignments.add(assignment);
                unassigned.remove(player);
            }
        }
    }

    private void assignAutofill(
            Set<Player> unassigned,
            Map<Role, List<PlayerAssignment>> roleAssignments,
            List<PlayerAssignment> assignments) {

        List<Player> remaining = new ArrayList<>(unassigned);
        remaining.sort(Comparator.comparingInt(Player::mmr).reversed());

        for (Player player : remaining) {
            for (Role role : Role.values()) {
                List<PlayerAssignment> roleList = roleAssignments.get(role);
                if (roleList.size() < PLAYERS_PER_ROLE) {
                    PlayerAssignment assignment = PlayerAssignment.create(player, role, AssignmentType.AUTOFILL);
                    roleList.add(assignment);
                    assignments.add(assignment);
                    unassigned.remove(player);
                    break;
                }
            }
        }
    }
}
