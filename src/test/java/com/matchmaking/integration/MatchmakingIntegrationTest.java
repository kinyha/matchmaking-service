package com.matchmaking.integration;

import com.matchmaking.algorithm.MmrWindowFinder;
import com.matchmaking.config.MatchmakingConfig;
import com.matchmaking.model.*;
import com.matchmaking.repository.InMemoryMatchRepository;
import com.matchmaking.repository.InMemoryQueueRepository;
import com.matchmaking.repository.MatchRepository;
import com.matchmaking.repository.QueueRepository;
import com.matchmaking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static com.matchmaking.integration.MatchAnalytics.*;
import static org.assertj.core.api.Assertions.*;

class MatchmakingIntegrationTest {
    private QueueRepository queueRepository;
    private MatchRepository matchRepository;
    private QueueService queueService;
    private MatchmakingService matchmakingService;
    private MatchmakingConfig config;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        queueRepository = new InMemoryQueueRepository();
        matchRepository = new InMemoryMatchRepository();
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        queueService = new QueueService(queueRepository, fixedClock);
        RoleAssignmentService roleAssignmentService = new RoleAssignmentService();
        TeamBalancerService teamBalancerService = new TeamBalancerService();
        MmrWindowFinder mmrWindowFinder = new MmrWindowFinder();
        config = new MatchmakingConfig();

        matchmakingService = new MatchmakingService(
                queueService,
                roleAssignmentService,
                teamBalancerService,
                mmrWindowFinder,
                matchRepository,
                config,
                fixedClock
        );
    }

    @Test
    @DisplayName("Full Flow: 30 players -> 3 matches with analytics")
    void fullFlow_withThirtyPlayers_createsThreeMatches() {
        System.out.println("\n=== TEST: 30 Players -> 3 Matches ===");

        // Given: 30 players with diverse roles and MMR
        List<Player> players = generateDiversePlayers(30);
        for (Player player : players) {
            queueService.enqueue(player);
        }
        printQueueStatus(queueService.getQueueSize(), 0);
        assertThat(queueService.getQueueSize()).isEqualTo(30);

        // When: Create matches until queue is exhausted
        List<Match> createdMatches = new ArrayList<>();
        int matchNum = 0;
        while (queueService.getQueueSize() >= 10) {
            MatchResult result = matchmakingService.tryCreateMatch();
            matchNum++;
            printMatchCreationResult(result, matchNum);

            assertThat(result.isSuccess()).isTrue();
            Match match = result.getMatch().get();
            createdMatches.add(match);

            printMatchReport(match, matchNum);
            printQueueStatus(queueService.getQueueSize(), matchNum);
        }

        // Then: 3 matches created, queue empty
        assertThat(createdMatches).hasSize(3);
        assertThat(queueService.getQueueSize()).isEqualTo(0);
        assertThat(matchRepository.count()).isEqualTo(3);

        printSummary(3, 30, createdMatches);

        // Verify each match quality
        for (Match match : createdMatches) {
            verifyMatchQuality(match);
        }
    }

    @Test
    @DisplayName("Full Flow: Players join and leave queue")
    void fullFlow_playersJoinAndLeave_handlesCorrectly() {
        System.out.println("\n=== TEST: Players Join and Leave ===");

        // Given: 15 players join
        List<Player> players = generateDiversePlayers(15);
        for (Player player : players) {
            queueService.enqueue(player);
        }
        System.out.println("[QUEUE] 15 players joined");
        printQueueStatus(queueService.getQueueSize(), 0);

        // When: 3 players leave
        queueService.dequeue("player_0");
        queueService.dequeue("player_5");
        queueService.dequeue("player_10");
        System.out.println("[QUEUE] 3 players left (player_0, player_5, player_10)");
        printQueueStatus(queueService.getQueueSize(), 0);

        // Then: 12 players remain
        assertThat(queueService.getQueueSize()).isEqualTo(12);

        // When: Create a match
        MatchResult result = matchmakingService.tryCreateMatch();
        printMatchCreationResult(result, 1);

        // Then: Success, 2 players left
        assertThat(result.isSuccess()).isTrue();
        assertThat(queueService.getQueueSize()).isEqualTo(2);

        printMatchReport(result.getMatch().get(), 1);
        printQueueStatus(queueService.getQueueSize(), 1);
    }

    @Test
    @DisplayName("Full Flow: Not enough players - graceful failure")
    void fullFlow_notEnoughPlayers_failsGracefully() {
        System.out.println("\n=== TEST: Not Enough Players ===");

        // Given: Only 8 players
        List<Player> players = generateDiversePlayers(8);
        for (Player player : players) {
            queueService.enqueue(player);
        }
        printQueueStatus(queueService.getQueueSize(), 0);

        // When: Try to create match
        MatchResult result = matchmakingService.tryCreateMatch();
        printMatchCreationResult(result, 1);

        // Then: Fails with reason
        assertThat(result.isFailure()).isTrue();
        assertThat(result.failureReason()).contains("Not enough players");
        assertThat(queueService.getQueueSize()).isEqualTo(8);

        System.out.println("[INFO] Players remain in queue, waiting for more players");
    }

    @Test
    @DisplayName("Full Flow: Duplicate enqueue throws exception")
    void fullFlow_duplicateEnqueue_throwsException() {
        System.out.println("\n=== TEST: Duplicate Enqueue Prevention ===");

        Player player = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);
        queueService.enqueue(player);
        System.out.println("[QUEUE] Player 'Player1' joined");

        System.out.println("[QUEUE] Attempting to add 'Player1' again...");
        assertThatThrownBy(() -> queueService.enqueue(player))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in queue");

        System.out.println("[OK] Duplicate rejected as expected");
    }

    @Test
    @DisplayName("Full Flow: Match quality metrics within thresholds")
    void fullFlow_matchQualityMetrics_withinThresholds() {
        System.out.println("\n=== TEST: Match Quality Metrics ===");

        // Given: 10 players with similar MMR
        Role[] roles = Role.values();
        System.out.println("[SETUP] Creating 10 players with MMR range 1500-1545");
        for (int i = 0; i < 10; i++) {
            Role primary = roles[i % 5];
            Role secondary = roles[(i + 1) % 5];
            Player player = Player.create(
                    "p" + i,
                    "Player" + i,
                    1500 + (i * 5),
                    primary,
                    secondary
            );
            queueService.enqueue(player);
        }
        printQueueStatus(queueService.getQueueSize(), 0);

        // When: Create match
        MatchResult result = matchmakingService.tryCreateMatch();
        printMatchCreationResult(result, 1);

        // Then: High quality match
        assertThat(result.isSuccess()).isTrue();
        Match match = result.getMatch().get();

        printMatchReport(match, 1);

        assertThat(match.mmrDifference()).isLessThanOrEqualTo(config.getMaxMmrDiff());
        assertThat(match.avgMmr()).isBetween(1500, 1550);
    }

    @Test
    @DisplayName("Full Flow: Perfect role distribution - all primary")
    void fullFlow_roleDistribution_allRolesCovered() {
        System.out.println("\n=== TEST: Perfect Role Distribution ===");

        // Given: 10 players with perfect role distribution
        Role[] roles = Role.values();
        System.out.println("[SETUP] Creating 2 players per role (perfect distribution)");
        for (int i = 0; i < 10; i++) {
            Role primary = roles[i / 2];
            Role secondary = roles[(i / 2 + 1) % 5];
            Player player = Player.create(
                    "p" + i,
                    "Player" + i,
                    1500,
                    primary,
                    secondary
            );
            queueService.enqueue(player);
        }
        printQueueStatus(queueService.getQueueSize(), 0);

        // When: Create match
        MatchResult result = matchmakingService.tryCreateMatch();
        printMatchCreationResult(result, 1);

        // Then: All roles covered in both teams
        assertThat(result.isSuccess()).isTrue();
        Match match = result.getMatch().get();

        printMatchReport(match, 1);

        for (Role role : Role.values()) {
            assertThat(match.team1().getPlayer(role)).isNotNull();
            assertThat(match.team2().getPlayer(role)).isNotNull();
        }
    }

    @Test
    @DisplayName("Full Flow: Autofill scenario - all MID/TOP players")
    void fullFlow_autofillScenario_stillCreatesMatch() {
        System.out.println("\n=== TEST: Autofill Scenario (Worst Case) ===");

        // Given: 10 players all wanting MID/TOP (worst case)
        System.out.println("[SETUP] All 10 players want MID/TOP - maximum autofill scenario");
        for (int i = 0; i < 10; i++) {
            Player player = Player.create(
                    "p" + i,
                    "MidMain" + i,
                    1500 + i * 10,
                    Role.MID,
                    Role.TOP
            );
            queueService.enqueue(player);
        }
        printQueueStatus(queueService.getQueueSize(), 0);

        // When: Create match
        MatchResult result = matchmakingService.tryCreateMatch();
        printMatchCreationResult(result, 1);

        // Then: Match created with autofill
        assertThat(result.isSuccess()).isTrue();
        Match match = result.getMatch().get();

        printMatchReport(match, 1);

        // Count autofills
        long team1Autofills = countAutofills(match.team1());
        long team2Autofills = countAutofills(match.team2());

        System.out.printf("[ANALYSIS] Team 1 autofills: %d, Team 2 autofills: %d%n",
                team1Autofills, team2Autofills);

        // Autofill should be used for JUNGLE, ADC, SUPPORT (3 roles * 2 teams = 6 autofills)
        assertThat(team1Autofills + team2Autofills).isEqualTo(6);
    }

    @Test
    @DisplayName("Full Flow: MMR window selection - picks closest group")
    void fullFlow_mmrWindowSelection_selectsClosestPlayers() {
        System.out.println("\n=== TEST: MMR Window Selection ===");

        // Given: Players with wide MMR spread
        System.out.println("[SETUP] Creating 3 MMR groups:");
        System.out.println("  - Low group:  5 players @ 500-580 MMR");
        System.out.println("  - Mid group:  10 players @ 1500-1590 MMR (best window)");
        System.out.println("  - High group: 5 players @ 2500-2580 MMR");

        // Low MMR group: 500-600
        for (int i = 0; i < 5; i++) {
            Role primary = Role.values()[i];
            Role secondary = Role.values()[(i + 1) % 5];
            queueService.enqueue(Player.create("low" + i, "Bronze" + i, 500 + i * 20, primary, secondary));
        }
        // High MMR group: 2500-2600
        for (int i = 0; i < 5; i++) {
            Role primary = Role.values()[i];
            Role secondary = Role.values()[(i + 1) % 5];
            queueService.enqueue(Player.create("high" + i, "Diamond" + i, 2500 + i * 20, primary, secondary));
        }
        // Mid MMR group: 1500-1600 (best window)
        for (int i = 0; i < 10; i++) {
            Role primary = Role.values()[i % 5];
            Role secondary = Role.values()[(i + 1) % 5];
            queueService.enqueue(Player.create("mid" + i, "Gold" + i, 1500 + i * 10, primary, secondary));
        }

        printQueueStatus(queueService.getQueueSize(), 0);

        // When: Create match
        MatchResult result = matchmakingService.tryCreateMatch();
        printMatchCreationResult(result, 1);

        // Then: Should select mid MMR group (smallest spread)
        assertThat(result.isSuccess()).isTrue();
        Match match = result.getMatch().get();

        printMatchReport(match, 1);

        // Average should be around 1545 (mid group), not mixed
        System.out.printf("[ANALYSIS] Match avg MMR: %d (expected ~1545 from mid group)%n", match.avgMmr());
        assertThat(match.avgMmr()).isBetween(1450, 1650);
    }

    @Test
    @DisplayName("Full Flow: 50 players -> 5 matches, no duplicates")
    void fullFlow_consecutiveMatches_maintainsIntegrity() {
        System.out.println("\n=== TEST: Consecutive Matches (50 Players) ===");

        // Given: 50 players
        List<Player> players = generateDiversePlayers(50);
        Set<String> allPlayerIds = new HashSet<>();
        for (Player player : players) {
            queueService.enqueue(player);
            allPlayerIds.add(player.id());
        }
        printQueueStatus(queueService.getQueueSize(), 0);

        // When: Create matches until not enough players
        Set<String> matchedPlayerIds = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        int matchCount = 0;

        while (true) {
            MatchResult result = matchmakingService.tryCreateMatch();
            if (result.isFailure()) {
                printMatchCreationResult(result, matchCount + 1);
                break;
            }
            matchCount++;
            Match match = result.getMatch().get();
            matches.add(match);

            printMatchCreationResult(result, matchCount);

            // Collect matched player IDs
            match.team1().roster().values().forEach(a -> matchedPlayerIds.add(a.player().id()));
            match.team2().roster().values().forEach(a -> matchedPlayerIds.add(a.player().id()));

            printQueueStatus(queueService.getQueueSize(), matchCount);
        }

        // Then: 5 matches created, no player matched twice
        assertThat(matchCount).isEqualTo(5);
        assertThat(matchedPlayerIds).hasSize(50);
        assertThat(queueService.getQueueSize()).isEqualTo(0);

        printSummary(matchCount, 50, matches);
    }

    @Test
    @DisplayName("Full Flow: Team balance with varied MMR")
    void fullFlow_verifyTeamBalance_mmrDifferenceMinimized() {
        System.out.println("\n=== TEST: Team Balance Verification ===");

        // Given: Players with varied MMR
        int[] mmrs = {1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900};
        Role[] roles = Role.values();

        System.out.println("[SETUP] Creating players with MMR spread: 1000-1900");
        for (int i = 0; i < 10; i++) {
            Role primary = roles[i % 5];
            Role secondary = roles[(i + 1) % 5];
            String name = getRankName(mmrs[i]) + i;
            queueService.enqueue(Player.create("p" + i, name, mmrs[i], primary, secondary));
        }
        printQueueStatus(queueService.getQueueSize(), 0);

        // When: Create match
        MatchResult result = matchmakingService.tryCreateMatch();
        printMatchCreationResult(result, 1);

        // Then: Teams should be balanced
        assertThat(result.isSuccess()).isTrue();
        Match match = result.getMatch().get();

        printMatchReport(match, 1);

        int team1Avg = match.team1().avgEffectiveMmr();
        int team2Avg = match.team2().avgEffectiveMmr();

        System.out.printf("%n[BALANCE ANALYSIS]%n");
        System.out.printf("  Team 1 Effective Avg: %d%n", team1Avg);
        System.out.printf("  Team 2 Effective Avg: %d%n", team2Avg);
        System.out.printf("  Difference: %d%n", Math.abs(team1Avg - team2Avg));

        // With good balancing, difference should be small
        assertThat(Math.abs(team1Avg - team2Avg)).isLessThanOrEqualTo(100);
    }

    private List<Player> generateDiversePlayers(int count) {
        List<Player> players = new ArrayList<>();
        Role[] roles = Role.values();
        Random random = new Random(42); // Fixed seed for reproducibility

        for (int i = 0; i < count; i++) {
            Role primary = roles[i % 5];
            Role secondary = roles[(i + 1) % 5];
            int mmr = 1200 + random.nextInt(600); // MMR range: 1200-1800

            players.add(Player.create(
                    "player_" + i,
                    "Player " + i,
                    mmr,
                    primary,
                    secondary
            ));
        }
        return players;
    }

    private void verifyMatchQuality(Match match) {
        // Verify team sizes
        assertThat(match.team1().size()).isEqualTo(5);
        assertThat(match.team2().size()).isEqualTo(5);

        // Verify all roles present
        for (Role role : Role.values()) {
            assertThat(match.team1().getPlayer(role))
                    .as("Team1 should have " + role)
                    .isNotNull();
            assertThat(match.team2().getPlayer(role))
                    .as("Team2 should have " + role)
                    .isNotNull();
        }

        // Verify MMR difference within threshold
        assertThat(match.mmrDifference())
                .as("MMR difference should be within threshold")
                .isLessThanOrEqualTo(config.getMaxMmrDiff());

        // Verify no duplicate players
        Set<String> playerIds = new HashSet<>();
        match.team1().roster().values().forEach(a -> playerIds.add(a.player().id()));
        match.team2().roster().values().forEach(a -> playerIds.add(a.player().id()));
        assertThat(playerIds).hasSize(10);
    }

    private long countAutofills(Team team) {
        return team.roster().values().stream()
                .filter(a -> a.assignmentType() == AssignmentType.AUTOFILL)
                .count();
    }

    private String getRankName(int mmr) {
        if (mmr < 500) return "Iron";
        if (mmr < 1000) return "Bronze";
        if (mmr < 1500) return "Silver";
        if (mmr < 2000) return "Gold";
        if (mmr < 2500) return "Plat";
        return "Diamond";
    }
}
