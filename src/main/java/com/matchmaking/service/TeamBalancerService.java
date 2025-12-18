package com.matchmaking.service;

import com.matchmaking.model.PlayerAssignment;
import com.matchmaking.model.Role;
import com.matchmaking.model.Team;

import java.util.*;

public class TeamBalancerService {

    public record TeamBalanceResult(
            Team team1,
            Team team2,
            int mmrDifference
    ) {}

    public TeamBalanceResult balanceTeams(List<PlayerAssignment> assignments) {
        if (assignments.size() != 10) {
            throw new IllegalArgumentException("Must have exactly 10 players");
        }

        // Group by role
        Map<Role, List<PlayerAssignment>> byRole = new EnumMap<>(Role.class);
        for (PlayerAssignment assignment : assignments) {
            byRole.computeIfAbsent(assignment.assignedRole(), k -> new ArrayList<>())
                    .add(assignment);
        }

        // Validate each role has exactly 2 players
        for (Role role : Role.values()) {
            List<PlayerAssignment> roleAssignments = byRole.get(role);
            if (roleAssignments == null || roleAssignments.size() != 2) {
                throw new IllegalArgumentException("Each role must have exactly 2 players");
            }
        }

        // Snake draft by effective MMR for each role
        List<PlayerAssignment> team1Assignments = new ArrayList<>();
        List<PlayerAssignment> team2Assignments = new ArrayList<>();

        // Sort roles by the MMR difference between their two players (descending)
        // This helps balance by assigning most impactful roles first
        List<Role> sortedRoles = new ArrayList<>(Arrays.asList(Role.values()));
        sortedRoles.sort((r1, r2) -> {
            int diff1 = getMmrDiff(byRole.get(r1));
            int diff2 = getMmrDiff(byRole.get(r2));
            return Integer.compare(diff2, diff1);
        });

        int team1Total = 0;
        int team2Total = 0;

        for (Role role : sortedRoles) {
            List<PlayerAssignment> rolePlayers = byRole.get(role);
            // Sort by effective MMR descending
            rolePlayers.sort(Comparator.comparingInt(PlayerAssignment::effectiveMmr).reversed());

            PlayerAssignment higher = rolePlayers.get(0);
            PlayerAssignment lower = rolePlayers.get(1);

            // Assign higher MMR player to team with lower total
            if (team1Total <= team2Total) {
                team1Assignments.add(higher);
                team2Assignments.add(lower);
                team1Total += higher.effectiveMmr();
                team2Total += lower.effectiveMmr();
            } else {
                team1Assignments.add(lower);
                team2Assignments.add(higher);
                team1Total += lower.effectiveMmr();
                team2Total += higher.effectiveMmr();
            }
        }

        Team team1 = Team.create(team1Assignments);
        Team team2 = Team.create(team2Assignments);
        int mmrDiff = Math.abs(team1.avgEffectiveMmr() - team2.avgEffectiveMmr());

        return new TeamBalanceResult(team1, team2, mmrDiff);
    }

    private int getMmrDiff(List<PlayerAssignment> players) {
        if (players.size() != 2) return 0;
        return Math.abs(players.get(0).effectiveMmr() - players.get(1).effectiveMmr());
    }
}
