package com.matchmaking.service;

import com.matchmaking.model.AssignmentType;
import com.matchmaking.model.Player;
import com.matchmaking.model.PlayerAssignment;
import com.matchmaking.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TeamBalancerServiceTest {
    private TeamBalancerService teamBalancerService;

    @BeforeEach
    void setUp() {
        teamBalancerService = new TeamBalancerService();
    }

    @Test
    void balanceTeams_throwsException_whenNotTenPlayers() {
        List<PlayerAssignment> assignments = createAssignments(5);

        assertThatThrownBy(() -> teamBalancerService.balanceTeams(assignments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 players");
    }

    @Test
    void balanceTeams_createsTwoTeamsOfFive() {
        List<PlayerAssignment> assignments = createBalancedAssignments();

        TeamBalancerService.TeamBalanceResult result = teamBalancerService.balanceTeams(assignments);

        assertThat(result.team1().size()).isEqualTo(5);
        assertThat(result.team2().size()).isEqualTo(5);
    }

    @Test
    void balanceTeams_eachTeamHasAllRoles() {
        List<PlayerAssignment> assignments = createBalancedAssignments();

        TeamBalancerService.TeamBalanceResult result = teamBalancerService.balanceTeams(assignments);

        for (Role role : Role.values()) {
            assertThat(result.team1().getPlayer(role)).isNotNull();
            assertThat(result.team2().getPlayer(role)).isNotNull();
        }
    }

    @Test
    void balanceTeams_minimizesMmrDifference() {
        List<PlayerAssignment> assignments = createBalancedAssignments();

        TeamBalancerService.TeamBalanceResult result = teamBalancerService.balanceTeams(assignments);

        // With balanced input, difference should be small
        assertThat(result.mmrDifference()).isLessThanOrEqualTo(50);
    }

    @Test
    void balanceTeams_handlesIdenticalMmr() {
        List<PlayerAssignment> assignments = new ArrayList<>();
        Role[] roles = Role.values();
        for (int i = 0; i < 10; i++) {
            Role role = roles[i / 2];
            Player player = Player.create("p" + i, "Player" + i, 1500, role, roles[(i / 2 + 1) % 5]);
            assignments.add(PlayerAssignment.create(player, role, AssignmentType.PRIMARY));
        }

        TeamBalancerService.TeamBalanceResult result = teamBalancerService.balanceTeams(assignments);

        assertThat(result.mmrDifference()).isEqualTo(0);
    }

    @Test
    void balanceTeams_handlesExtremeMmrDifference() {
        List<PlayerAssignment> assignments = new ArrayList<>();
        Role[] roles = Role.values();
        // Create 5 high MMR and 5 low MMR players
        for (int i = 0; i < 10; i++) {
            Role role = roles[i / 2];
            int mmr = (i % 2 == 0) ? 2500 : 500;
            Player player = Player.create("p" + i, "Player" + i, mmr, role, roles[(i / 2 + 1) % 5]);
            assignments.add(PlayerAssignment.create(player, role, AssignmentType.PRIMARY));
        }

        TeamBalancerService.TeamBalanceResult result = teamBalancerService.balanceTeams(assignments);

        // Algorithm should distribute evenly
        assertThat(result.team1().size()).isEqualTo(5);
        assertThat(result.team2().size()).isEqualTo(5);
        // Greedy algorithm minimizes but may not achieve perfect 0
        // With 5 roles and alternating assignment, difference should be reasonable
        assertThat(result.mmrDifference()).isLessThanOrEqualTo(400);
    }

    @Test
    void balanceTeams_calculatesCorrectAverageMmr() {
        List<PlayerAssignment> assignments = createBalancedAssignments();

        TeamBalancerService.TeamBalanceResult result = teamBalancerService.balanceTeams(assignments);

        int team1Sum = result.team1().roster().values().stream()
                .mapToInt(a -> a.player().mmr())
                .sum();
        int team2Sum = result.team2().roster().values().stream()
                .mapToInt(a -> a.player().mmr())
                .sum();

        assertThat(result.team1().avgMmr()).isEqualTo(team1Sum / 5);
        assertThat(result.team2().avgMmr()).isEqualTo(team2Sum / 5);
    }

    private List<PlayerAssignment> createAssignments(int count) {
        List<PlayerAssignment> assignments = new ArrayList<>();
        Role[] roles = Role.values();
        for (int i = 0; i < count; i++) {
            Role role = roles[i % 5];
            Player player = Player.create("p" + i, "Player" + i, 1500 + i * 10, role, roles[(i + 1) % 5]);
            assignments.add(PlayerAssignment.create(player, role, AssignmentType.PRIMARY));
        }
        return assignments;
    }

    private List<PlayerAssignment> createBalancedAssignments() {
        List<PlayerAssignment> assignments = new ArrayList<>();
        Role[] roles = Role.values();
        // 2 players per role with slightly different MMR
        for (int i = 0; i < 10; i++) {
            Role role = roles[i / 2];
            int mmr = 1500 + (i % 2) * 20;  // 1500 and 1520 alternating
            Player player = Player.create("p" + i, "Player" + i, mmr, role, roles[(i / 2 + 1) % 5]);
            assignments.add(PlayerAssignment.create(player, role, AssignmentType.PRIMARY));
        }
        return assignments;
    }
}
