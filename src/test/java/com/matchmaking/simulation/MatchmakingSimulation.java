package com.matchmaking.simulation;

import com.matchmaking.algorithm.MmrWindowFinder;
import com.matchmaking.config.MatchmakingConfig;
import com.matchmaking.model.*;
import com.matchmaking.repository.OptimizedQueueRepository;
import com.matchmaking.repository.InMemoryMatchRepository;
import com.matchmaking.repository.MatchRepository;
import com.matchmaking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Large-scale simulation test with 10,000 players
 * Analyzes match quality, wait times, and role satisfaction
 */
class MatchmakingSimulation {
    private OptimizedQueueRepository queueRepository;
    private MatchRepository matchRepository;
    private QueueService queueService;
    private MatchmakingService matchmakingService;
    private MatchmakingConfig config;
    private Clock clock;

    // Simulation parameters
    private static final int TOTAL_PLAYERS = 10_000;
    private static final int TARGET_MATCHES = 1_000;
    private static final Random RANDOM = new Random(42);

    // Analytics
    private final List<MatchAnalytics> matchAnalytics = new ArrayList<>();
    private final Map<String, PlayerAnalytics> playerAnalytics = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        queueRepository = new OptimizedQueueRepository();
        matchRepository = new InMemoryMatchRepository();
        clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        queueService = new QueueService(queueRepository, clock);

        RoleAssignmentService roleAssignmentService = new RoleAssignmentService();
        TeamBalancerService teamBalancerService = new TeamBalancerService();
        MmrWindowFinder mmrWindowFinder = new MmrWindowFinder();
        config = new MatchmakingConfig();
        config.setMaxMmrDiff(150); // Slightly relaxed for simulation

        matchmakingService = new MatchmakingService(
                queueService,
                roleAssignmentService,
                teamBalancerService,
                mmrWindowFinder,
                matchRepository,
                config,
                clock
        );
    }

    @Test
    @DisplayName("Simulation: 10,000 players -> 1,000 matches")
    void runFullSimulation() {
        printHeader("MATCHMAKING SIMULATION");
        printConfig();

        // Phase 1: Generate players
        System.out.println("\n[PHASE 1] Generating " + TOTAL_PLAYERS + " players...");
        long startGen = System.currentTimeMillis();
        List<Player> players = generatePlayers(TOTAL_PLAYERS);
        long genTime = System.currentTimeMillis() - startGen;
        System.out.printf("Generated %d players in %dms%n", players.size(), genTime);

        // Phase 2: Enqueue all players
        System.out.println("\n[PHASE 2] Enqueueing players...");
        long startEnqueue = System.currentTimeMillis();
        for (Player player : players) {
            queueService.enqueue(player);
            playerAnalytics.put(player.id(), new PlayerAnalytics(player));
        }
        long enqueueTime = System.currentTimeMillis() - startEnqueue;
        System.out.printf("Enqueued %d players in %dms (%.0f players/sec)%n",
                players.size(), enqueueTime, players.size() * 1000.0 / enqueueTime);

        // Print MMR distribution
        printMmrDistribution();

        // Phase 3: Create matches
        System.out.println("\n[PHASE 3] Creating matches...");
        long startMatch = System.currentTimeMillis();
        int matchesCreated = 0;
        int matchesFailed = 0;

        while (queueService.getQueueSize() >= 10 && matchesCreated < TARGET_MATCHES) {
            MatchResult result = matchmakingService.tryCreateMatch();

            if (result.isSuccess()) {
                Match match = result.getMatch().get();
                matchesCreated++;
                analyzeMatch(match, matchesCreated);

                if (matchesCreated % 100 == 0) {
                    System.out.printf("  Created %d matches, queue size: %d%n",
                            matchesCreated, queueService.getQueueSize());
                }
            } else {
                matchesFailed++;
                if (matchesFailed > 10) {
                    System.out.println("  Too many failures, stopping...");
                    break;
                }
            }
        }

        long matchTime = System.currentTimeMillis() - startMatch;
        System.out.printf("Created %d matches in %dms (%.1f matches/sec)%n",
                matchesCreated, matchTime, matchesCreated * 1000.0 / matchTime);

        // Phase 4: Print results
        printResults(matchesCreated, matchesFailed);

        // Assertions
        assertThat(matchesCreated).isGreaterThanOrEqualTo(TARGET_MATCHES);
    }

    @Test
    @DisplayName("Simulation: Role shortage scenario")
    void simulateRoleShortage() {
        printHeader("ROLE SHORTAGE SIMULATION");

        // Create players with mostly MID preference (common in real games)
        System.out.println("[SETUP] Creating 100 players with MID-heavy preference");
        for (int i = 0; i < 100; i++) {
            Role primary;
            Role secondary;

            double roll = RANDOM.nextDouble();
            if (roll < 0.35) {
                primary = Role.MID;
                secondary = Role.values()[RANDOM.nextInt(4)]; // Not MID
                if (secondary == Role.MID) secondary = Role.SUPPORT;
            } else if (roll < 0.55) {
                primary = Role.ADC;
                secondary = Role.SUPPORT;
            } else if (roll < 0.75) {
                primary = Role.TOP;
                secondary = Role.JUNGLE;
            } else if (roll < 0.90) {
                primary = Role.JUNGLE;
                secondary = Role.TOP;
            } else {
                primary = Role.SUPPORT;
                secondary = Role.ADC;
            }

            Player player = Player.create(
                    "p" + i,
                    "Player" + i,
                    1500 + RANDOM.nextInt(200),
                    primary,
                    secondary
            );
            queueService.enqueue(player);
        }

        // Create matches
        List<Match> matches = new ArrayList<>();
        while (queueService.getQueueSize() >= 10) {
            MatchResult result = matchmakingService.tryCreateMatch();
            if (result.isSuccess()) {
                matches.add(result.getMatch().get());
            }
        }

        // Analyze role distribution
        int[] primaryCount = new int[1];
        int[] secondaryCount = new int[1];
        int[] autofillCount = new int[1];

        for (Match match : matches) {
            match.team1().roster().values().forEach(a -> countAssignment(a, primaryCount, secondaryCount, autofillCount));
            match.team2().roster().values().forEach(a -> countAssignment(a, primaryCount, secondaryCount, autofillCount));
        }

        int total = primaryCount[0] + secondaryCount[0] + autofillCount[0];

        System.out.println("\n[RESULTS] Role Shortage Handling");
        System.out.println("═".repeat(50));
        System.out.printf("Matches created: %d%n", matches.size());
        System.out.printf("Primary:   %3d (%5.1f%%)%n", primaryCount[0], primaryCount[0] * 100.0 / total);
        System.out.printf("Secondary: %3d (%5.1f%%)%n", secondaryCount[0], secondaryCount[0] * 100.0 / total);
        System.out.printf("Autofill:  %3d (%5.1f%%)%n", autofillCount[0], autofillCount[0] * 100.0 / total);

        assertThat(matches.size()).isGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("Simulation: Performance benchmark")
    void benchmarkPerformance() {
        printHeader("PERFORMANCE BENCHMARK");

        int[] sizes = {100, 500, 1000, 5000, 10000};

        System.out.println("Queue Size | Enqueue Time | Match Time | Matches/sec");
        System.out.println("─".repeat(55));

        for (int size : sizes) {
            queueRepository.clear();

            // Benchmark enqueue
            List<Player> players = generatePlayers(size);
            long startEnqueue = System.nanoTime();
            for (Player player : players) {
                queueService.enqueue(player);
            }
            long enqueueNanos = System.nanoTime() - startEnqueue;

            // Benchmark match creation
            int matchCount = 0;
            long startMatch = System.nanoTime();
            while (queueService.getQueueSize() >= 10) {
                MatchResult result = matchmakingService.tryCreateMatch();
                if (result.isSuccess()) matchCount++;
                else break;
            }
            long matchNanos = System.nanoTime() - startMatch;

            double matchesPerSec = matchCount * 1_000_000_000.0 / matchNanos;

            System.out.printf("%10d | %10.2fms | %10.2fms | %10.1f%n",
                    size,
                    enqueueNanos / 1_000_000.0,
                    matchNanos / 1_000_000.0,
                    matchesPerSec);
        }
    }

    private List<Player> generatePlayers(int count) {
        return IntStream.range(0, count)
                .mapToObj(this::generatePlayer)
                .collect(Collectors.toList());
    }

    private Player generatePlayer(int index) {
        // MMR distribution: normal distribution centered at 1500
        int mmr = (int) (1500 + RANDOM.nextGaussian() * 400);
        mmr = Math.max(0, Math.min(3000, mmr)); // Clamp to valid range

        // Role preferences with realistic distribution
        Role primary = getWeightedRole();
        Role secondary;
        do {
            secondary = getWeightedRole();
        } while (secondary == primary);

        return Player.create(
                "player_" + index,
                generatePlayerName(index),
                mmr,
                primary,
                secondary
        );
    }

    private Role getWeightedRole() {
        // Realistic role popularity: MID > ADC > TOP > JUNGLE > SUPPORT
        double roll = RANDOM.nextDouble();
        if (roll < 0.25) return Role.MID;
        if (roll < 0.45) return Role.ADC;
        if (roll < 0.65) return Role.TOP;
        if (roll < 0.82) return Role.JUNGLE;
        return Role.SUPPORT;
    }

    private String generatePlayerName(int index) {
        String[] prefixes = {"Pro", "xX", "The", "Dark", "Shadow", "Fire", "Ice", "Storm"};
        String[] suffixes = {"Slayer", "Master", "King", "Lord", "Hunter", "Ninja", "Wolf", "Dragon"};
        return prefixes[index % prefixes.length] + suffixes[(index / prefixes.length) % suffixes.length] + index;
    }

    private void analyzeMatch(Match match, int matchNumber) {
        MatchAnalytics analytics = new MatchAnalytics();
        analytics.matchNumber = matchNumber;
        analytics.mmrDifference = match.mmrDifference();
        analytics.avgMmr = match.avgMmr();

        // Count role assignments
        for (PlayerAssignment a : match.team1().roster().values()) {
            analytics.countAssignment(a.assignmentType());
            updatePlayerAnalytics(a);
        }
        for (PlayerAssignment a : match.team2().roster().values()) {
            analytics.countAssignment(a.assignmentType());
            updatePlayerAnalytics(a);
        }

        matchAnalytics.add(analytics);
    }

    private void updatePlayerAnalytics(PlayerAssignment assignment) {
        PlayerAnalytics pa = playerAnalytics.get(assignment.player().id());
        if (pa != null) {
            pa.matchesPlayed++;
            pa.lastAssignmentType = assignment.assignmentType();
            if (assignment.assignmentType() == AssignmentType.AUTOFILL) {
                pa.autofillCount++;
            }
        }
    }

    private void countAssignment(PlayerAssignment a, int[] primary, int[] secondary, int[] autofill) {
        switch (a.assignmentType()) {
            case PRIMARY -> primary[0]++;
            case SECONDARY -> secondary[0]++;
            case AUTOFILL -> autofill[0]++;
        }
    }

    private void printHeader(String title) {
        System.out.println("\n" + "═".repeat(80));
        System.out.println(" ".repeat((80 - title.length()) / 2) + title);
        System.out.println("═".repeat(80));
    }

    private void printConfig() {
        System.out.println("\n[CONFIG]");
        System.out.printf("  Total Players: %,d%n", TOTAL_PLAYERS);
        System.out.printf("  Target Matches: %,d%n", TARGET_MATCHES);
        System.out.printf("  Max MMR Diff: %d%n", config.getMaxMmrDiff());
        System.out.printf("  Players per Match: %d%n", config.getPlayersPerMatch());
    }

    private void printMmrDistribution() {
        Map<Integer, Integer> distribution = queueRepository.getBucketDistribution();

        System.out.println("\n[MMR DISTRIBUTION]");
        System.out.println("MMR Range    | Count | Distribution");
        System.out.println("─".repeat(55));

        int maxCount = distribution.values().stream().mapToInt(i -> i).max().orElse(1);

        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            int mmr = entry.getKey();
            int count = entry.getValue();
            int barLength = count * 30 / maxCount;
            String bar = "█".repeat(barLength);

            System.out.printf("%4d - %4d | %5d | %s%n", mmr, mmr + 99, count, bar);
        }
    }

    private void printResults(int matchesCreated, int matchesFailed) {
        printHeader("SIMULATION RESULTS");

        // Overall stats
        System.out.println("\n[OVERALL STATISTICS]");
        System.out.printf("  Matches Created: %,d%n", matchesCreated);
        System.out.printf("  Matches Failed: %d%n", matchesFailed);
        System.out.printf("  Players Matched: %,d%n", matchesCreated * 10);
        System.out.printf("  Players Remaining: %d%n", queueService.getQueueSize());

        // MMR difference analysis
        if (!matchAnalytics.isEmpty()) {
            DoubleSummaryStatistics mmrStats = matchAnalytics.stream()
                    .mapToDouble(a -> a.mmrDifference)
                    .summaryStatistics();

            System.out.println("\n[MMR DIFFERENCE ANALYSIS]");
            System.out.printf("  Average: %.1f%n", mmrStats.getAverage());
            System.out.printf("  Min: %.0f%n", mmrStats.getMin());
            System.out.printf("  Max: %.0f%n", mmrStats.getMax());

            // Percentiles
            List<Integer> sortedDiffs = matchAnalytics.stream()
                    .map(a -> a.mmrDifference)
                    .sorted()
                    .toList();

            System.out.printf("  P50: %d%n", sortedDiffs.get(sortedDiffs.size() / 2));
            System.out.printf("  P90: %d%n", sortedDiffs.get((int)(sortedDiffs.size() * 0.9)));
            System.out.printf("  P99: %d%n", sortedDiffs.get((int)(sortedDiffs.size() * 0.99)));

            // Quality distribution
            long perfect = matchAnalytics.stream().filter(a -> a.mmrDifference <= 20).count();
            long great = matchAnalytics.stream().filter(a -> a.mmrDifference > 20 && a.mmrDifference <= 50).count();
            long good = matchAnalytics.stream().filter(a -> a.mmrDifference > 50 && a.mmrDifference <= 100).count();
            long fair = matchAnalytics.stream().filter(a -> a.mmrDifference > 100).count();

            System.out.println("\n[MATCH QUALITY DISTRIBUTION]");
            System.out.printf("  PERFECT (≤20):  %,5d (%5.1f%%) %s%n",
                    perfect, perfect * 100.0 / matchesCreated, getBar(perfect, matchesCreated));
            System.out.printf("  GREAT (21-50):  %,5d (%5.1f%%) %s%n",
                    great, great * 100.0 / matchesCreated, getBar(great, matchesCreated));
            System.out.printf("  GOOD (51-100):  %,5d (%5.1f%%) %s%n",
                    good, good * 100.0 / matchesCreated, getBar(good, matchesCreated));
            System.out.printf("  FAIR (>100):    %,5d (%5.1f%%) %s%n",
                    fair, fair * 100.0 / matchesCreated, getBar(fair, matchesCreated));
        }

        // Role satisfaction analysis
        int totalPrimary = matchAnalytics.stream().mapToInt(a -> a.primaryCount).sum();
        int totalSecondary = matchAnalytics.stream().mapToInt(a -> a.secondaryCount).sum();
        int totalAutofill = matchAnalytics.stream().mapToInt(a -> a.autofillCount).sum();
        int totalAssignments = totalPrimary + totalSecondary + totalAutofill;

        System.out.println("\n[ROLE SATISFACTION]");
        System.out.printf("  Primary:   %,6d (%5.1f%%) %s%n",
                totalPrimary, totalPrimary * 100.0 / totalAssignments,
                getBar(totalPrimary, totalAssignments));
        System.out.printf("  Secondary: %,6d (%5.1f%%) %s%n",
                totalSecondary, totalSecondary * 100.0 / totalAssignments,
                getBar(totalSecondary, totalAssignments));
        System.out.printf("  Autofill:  %,6d (%5.1f%%) %s%n",
                totalAutofill, totalAutofill * 100.0 / totalAssignments,
                getBar(totalAutofill, totalAssignments));

        // Target metrics check
        System.out.println("\n[TARGET METRICS CHECK]");
        double primaryRate = totalPrimary * 100.0 / totalAssignments;
        double avgMmrDiff = matchAnalytics.stream().mapToDouble(a -> a.mmrDifference).average().orElse(0);
        double autofillRate = totalAutofill * 100.0 / totalAssignments;

        checkTarget("Primary Role Rate", primaryRate, 80, "%", true);
        checkTarget("Avg MMR Difference", avgMmrDiff, 50, "", false);
        checkTarget("Autofill Rate", autofillRate, 10, "%", false);

        System.out.println("\n" + "═".repeat(80));
    }

    private void checkTarget(String metric, double value, double target, String unit, boolean higherIsBetter) {
        boolean met = higherIsBetter ? value >= target : value <= target;
        String status = met ? "✓ PASS" : "✗ FAIL";
        String comparison = higherIsBetter ? "≥" : "≤";
        System.out.printf("  %s: %.1f%s (target %s %.0f%s) [%s]%n",
                metric, value, unit, comparison, target, unit, status);
    }

    private String getBar(long value, long total) {
        int length = (int) (value * 30 / total);
        return "[" + "█".repeat(length) + "░".repeat(30 - length) + "]";
    }

    private static class MatchAnalytics {
        int matchNumber;
        int mmrDifference;
        int avgMmr;
        int primaryCount;
        int secondaryCount;
        int autofillCount;

        void countAssignment(AssignmentType type) {
            switch (type) {
                case PRIMARY -> primaryCount++;
                case SECONDARY -> secondaryCount++;
                case AUTOFILL -> autofillCount++;
            }
        }
    }

    private static class PlayerAnalytics {
        final Player player;
        int matchesPlayed;
        int autofillCount;
        AssignmentType lastAssignmentType;

        PlayerAnalytics(Player player) {
            this.player = player;
        }
    }
}
