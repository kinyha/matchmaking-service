package com.matchmaking.service;

import com.matchmaking.model.AssignmentType;
import com.matchmaking.model.Player;
import com.matchmaking.model.PlayerAssignment;
import com.matchmaking.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class RoleAssignmentServiceTest {
    private RoleAssignmentService roleAssignmentService;

    @BeforeEach
    void setUp() {
        roleAssignmentService = new RoleAssignmentService();
    }

    @Test
    void assignRoles_returnsEmpty_whenNotTenPlayers() {
        List<Player> players = createPlayers(5);

        Optional<List<PlayerAssignment>> result = roleAssignmentService.assignRoles(players);

        assertThat(result).isEmpty();
    }

    @Test
    void assignRoles_assignsAllPrimaryRoles_whenPerfectDistribution() {
        List<Player> players = createPerfectDistributionPlayers();

        Optional<List<PlayerAssignment>> result = roleAssignmentService.assignRoles(players);

        assertThat(result).isPresent();
        List<PlayerAssignment> assignments = result.get();
        assertThat(assignments).hasSize(10);

        long primaryCount = assignments.stream()
                .filter(a -> a.assignmentType() == AssignmentType.PRIMARY)
                .count();
        assertThat(primaryCount).isEqualTo(10);
    }

    @Test
    void assignRoles_useSecondary_whenPrimaryUnavailable() {
        List<Player> players = new ArrayList<>();
        // 3 MID primary players - one will need secondary
        players.add(Player.create("p1", "Player1", 1500, Role.MID, Role.SUPPORT));
        players.add(Player.create("p2", "Player2", 1510, Role.MID, Role.SUPPORT));
        players.add(Player.create("p3", "Player3", 1520, Role.MID, Role.SUPPORT));
        // Others with diverse roles
        players.add(Player.create("p4", "Player4", 1530, Role.TOP, Role.MID));
        players.add(Player.create("p5", "Player5", 1540, Role.TOP, Role.JUNGLE));
        players.add(Player.create("p6", "Player6", 1550, Role.JUNGLE, Role.ADC));
        players.add(Player.create("p7", "Player7", 1560, Role.JUNGLE, Role.TOP));
        players.add(Player.create("p8", "Player8", 1570, Role.ADC, Role.MID));
        players.add(Player.create("p9", "Player9", 1580, Role.ADC, Role.TOP));
        players.add(Player.create("p10", "Player10", 1590, Role.SUPPORT, Role.MID));

        Optional<List<PlayerAssignment>> result = roleAssignmentService.assignRoles(players);

        assertThat(result).isPresent();
        List<PlayerAssignment> assignments = result.get();

        // Verify each role has exactly 2 players
        Map<Role, Long> roleCounts = new EnumMap<>(Role.class);
        for (PlayerAssignment assignment : assignments) {
            roleCounts.merge(assignment.assignedRole(), 1L, Long::sum);
        }
        for (Role role : Role.values()) {
            assertThat(roleCounts.get(role)).isEqualTo(2);
        }

        // Check that secondary roles were used (MID has 3 primary, only 2 slots)
        long secondaryCount = assignments.stream()
                .filter(a -> a.assignmentType() == AssignmentType.SECONDARY)
                .count();
        assertThat(secondaryCount).isGreaterThan(0);
    }

    @Test
    void assignRoles_usesAutofill_whenNecessary() {
        List<Player> players = new ArrayList<>();
        // All MID/TOP players - will need autofill for other roles
        for (int i = 0; i < 10; i++) {
            players.add(Player.create("p" + i, "Player" + i, 1500 + i * 10, Role.MID, Role.TOP));
        }

        Optional<List<PlayerAssignment>> result = roleAssignmentService.assignRoles(players);

        assertThat(result).isPresent();
        List<PlayerAssignment> assignments = result.get();

        long autofillCount = assignments.stream()
                .filter(a -> a.assignmentType() == AssignmentType.AUTOFILL)
                .count();
        assertThat(autofillCount).isGreaterThan(0);
    }

    @Test
    void assignRoles_calculatesEffectiveMmr() {
        List<Player> players = createPerfectDistributionPlayers();

        Optional<List<PlayerAssignment>> result = roleAssignmentService.assignRoles(players);

        assertThat(result).isPresent();
        for (PlayerAssignment assignment : result.get()) {
            int expectedPenalty = assignment.assignmentType().getMmrPenalty();
            int expectedEffectiveMmr = assignment.player().mmr() - expectedPenalty;
            assertThat(assignment.effectiveMmr()).isEqualTo(expectedEffectiveMmr);
        }
    }

    @Test
    void assignRoles_prioritizesHigherMmrPlayers() {
        List<Player> players = new ArrayList<>();
        // Two MID players with different MMR
        players.add(Player.create("high", "HighMMR", 2000, Role.MID, Role.TOP));
        players.add(Player.create("low", "LowMMR", 1000, Role.MID, Role.TOP));
        // Fill rest with diverse roles
        players.add(Player.create("p3", "Player3", 1500, Role.TOP, Role.JUNGLE));
        players.add(Player.create("p4", "Player4", 1500, Role.JUNGLE, Role.ADC));
        players.add(Player.create("p5", "Player5", 1500, Role.ADC, Role.SUPPORT));
        players.add(Player.create("p6", "Player6", 1500, Role.SUPPORT, Role.MID));
        players.add(Player.create("p7", "Player7", 1500, Role.TOP, Role.SUPPORT));
        players.add(Player.create("p8", "Player8", 1500, Role.JUNGLE, Role.TOP));
        players.add(Player.create("p9", "Player9", 1500, Role.ADC, Role.JUNGLE));
        players.add(Player.create("p10", "Player10", 1500, Role.SUPPORT, Role.ADC));

        Optional<List<PlayerAssignment>> result = roleAssignmentService.assignRoles(players);

        assertThat(result).isPresent();

        // High MMR player should get MID (primary)
        Optional<PlayerAssignment> highAssignment = result.get().stream()
                .filter(a -> a.player().id().equals("high"))
                .findFirst();
        assertThat(highAssignment).isPresent();
        assertThat(highAssignment.get().assignedRole()).isEqualTo(Role.MID);
        assertThat(highAssignment.get().assignmentType()).isEqualTo(AssignmentType.PRIMARY);
    }

    private List<Player> createPlayers(int count) {
        List<Player> players = new ArrayList<>();
        Role[] roles = Role.values();
        for (int i = 0; i < count; i++) {
            Role primary = roles[i % 5];
            Role secondary = roles[(i + 1) % 5];
            players.add(Player.create("p" + i, "Player" + i, 1500 + i * 10, primary, secondary));
        }
        return players;
    }

    private List<Player> createPerfectDistributionPlayers() {
        List<Player> players = new ArrayList<>();
        Role[] roles = Role.values();
        // 2 players per role with that role as primary
        for (int i = 0; i < 10; i++) {
            Role primary = roles[i / 2];
            Role secondary = roles[(i / 2 + 1) % 5];
            players.add(Player.create("p" + i, "Player" + i, 1500 + i * 10, primary, secondary));
        }
        return players;
    }
}
