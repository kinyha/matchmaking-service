package com.matchmaking.integration;

import com.matchmaking.model.*;

import java.util.Map;
import java.util.stream.Collectors;

public class MatchAnalytics {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_BOLD = "\u001B[1m";

    public static void printMatchReport(Match match, int matchNumber) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║                           MATCH #%-3d REPORT                                  ║%n", matchNumber));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Match ID: %-66s║%n", truncate(match.id(), 66)));
        sb.append(String.format("║  Created:  %-66s║%n", match.createdAt()));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");

        // Quality Metrics
        sb.append("║                            QUALITY METRICS                                   ║\n");
        sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");
        sb.append(String.format("║  Average MMR:      %-58d║%n", match.avgMmr()));
        sb.append(String.format("║  MMR Difference:   %-52d%s║%n",
                match.mmrDifference(),
                getQualityIndicator(match.mmrDifference())));
        sb.append(String.format("║  Team 1 Avg MMR:   %-58d║%n", match.team1().avgMmr()));
        sb.append(String.format("║  Team 2 Avg MMR:   %-58d║%n", match.team2().avgMmr()));

        // Role Assignment Stats
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║                          ROLE ASSIGNMENT STATS                               ║\n");
        sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");

        RoleStats stats = calculateRoleStats(match);
        sb.append(String.format("║  Primary Role:     %-3d / 10  (%3d%%)  %-33s║%n",
                stats.primary, stats.primary * 10, getRoleBar(stats.primary, 10)));
        sb.append(String.format("║  Secondary Role:   %-3d / 10  (%3d%%)  %-33s║%n",
                stats.secondary, stats.secondary * 10, getRoleBar(stats.secondary, 10)));
        sb.append(String.format("║  Autofill:         %-3d / 10  (%3d%%)  %-33s║%n",
                stats.autofill, stats.autofill * 10, getRoleBar(stats.autofill, 10)));

        // Teams
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║                               TEAM 1                                         ║\n");
        sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");
        sb.append(formatTeamRoster(match.team1()));

        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║                               TEAM 2                                         ║\n");
        sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");
        sb.append(formatTeamRoster(match.team2()));

        sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");

        System.out.println(sb);
    }

    public static void printQueueStatus(int queueSize, int matchesCreated) {
        System.out.printf("%n[QUEUE] Size: %d | Matches created: %d%n", queueSize, matchesCreated);
    }

    public static void printSummary(int totalMatches, int totalPlayers, java.util.List<Match> matches) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                         MATCHMAKING SUMMARY                                  ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Total Players Processed:  %-50d║%n", totalPlayers));
        sb.append(String.format("║  Total Matches Created:    %-50d║%n", totalMatches));
        sb.append(String.format("║  Players per Match:        %-50d║%n", 10));

        if (!matches.isEmpty()) {
            double avgMmrDiff = matches.stream()
                    .mapToInt(Match::mmrDifference)
                    .average()
                    .orElse(0);
            int maxMmrDiff = matches.stream()
                    .mapToInt(Match::mmrDifference)
                    .max()
                    .orElse(0);
            int minMmrDiff = matches.stream()
                    .mapToInt(Match::mmrDifference)
                    .min()
                    .orElse(0);

            sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");
            sb.append("║                          MMR DIFFERENCE STATS                                ║\n");
            sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");
            sb.append(String.format("║  Average:  %-66.1f║%n", avgMmrDiff));
            sb.append(String.format("║  Min:      %-66d║%n", minMmrDiff));
            sb.append(String.format("║  Max:      %-66d║%n", maxMmrDiff));

            // Role distribution across all matches
            int totalPrimary = 0, totalSecondary = 0, totalAutofill = 0;
            for (Match match : matches) {
                RoleStats stats = calculateRoleStats(match);
                totalPrimary += stats.primary;
                totalSecondary += stats.secondary;
                totalAutofill += stats.autofill;
            }
            int totalAssignments = totalPrimary + totalSecondary + totalAutofill;

            sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");
            sb.append("║                       OVERALL ROLE SATISFACTION                              ║\n");
            sb.append("╠──────────────────────────────────────────────────────────────────────────────╣\n");
            sb.append(String.format("║  Primary:    %3d / %3d  (%5.1f%%)  %-35s║%n",
                    totalPrimary, totalAssignments, (totalPrimary * 100.0 / totalAssignments),
                    getProgressBar(totalPrimary, totalAssignments)));
            sb.append(String.format("║  Secondary:  %3d / %3d  (%5.1f%%)  %-35s║%n",
                    totalSecondary, totalAssignments, (totalSecondary * 100.0 / totalAssignments),
                    getProgressBar(totalSecondary, totalAssignments)));
            sb.append(String.format("║  Autofill:   %3d / %3d  (%5.1f%%)  %-35s║%n",
                    totalAutofill, totalAssignments, (totalAutofill * 100.0 / totalAssignments),
                    getProgressBar(totalAutofill, totalAssignments)));
        }

        sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");

        System.out.println(sb);
    }

    public static void printMatchCreationResult(MatchResult result, int attemptNumber) {
        if (result.isSuccess()) {
            System.out.printf("[OK] Match #%d created successfully%n", attemptNumber);
        } else {
            System.out.printf("[FAIL] Match #%d failed: %s%n", attemptNumber, result.failureReason());
        }
    }

    private static String formatTeamRoster(Team team) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("║  %-8s │ %-15s │ %4s │ %4s │ %-10s │ %-8s  ║%n",
                "Role", "Player", "MMR", "Eff.", "Assignment", "Pref"));
        sb.append("║──────────┼─────────────────┼──────┼──────┼────────────┼──────────║\n");

        for (Role role : Role.values()) {
            PlayerAssignment assignment = team.getPlayer(role);
            if (assignment != null) {
                Player p = assignment.player();
                String prefIndicator = getPrefIndicator(p, assignment.assignedRole());
                sb.append(String.format("║  %-8s │ %-15s │ %4d │ %4d │ %-10s │ %-8s  ║%n",
                        role,
                        truncate(p.displayName(), 15),
                        p.mmr(),
                        assignment.effectiveMmr(),
                        assignment.assignmentType(),
                        prefIndicator));
            }
        }
        sb.append(String.format("║──────────┴─────────────────┴──────┴──────┴────────────┴──────────║%n"));
        sb.append(String.format("║  Team Avg MMR: %-10d    Team Avg Effective MMR: %-16d║%n",
                team.avgMmr(), team.avgEffectiveMmr()));
        return sb.toString();
    }

    private static String getPrefIndicator(Player player, Role assignedRole) {
        if (assignedRole == player.primaryRole()) {
            return "1st";
        } else if (assignedRole == player.secondaryRole()) {
            return "2nd";
        } else {
            return "Fill";
        }
    }

    private static String getQualityIndicator(int mmrDiff) {
        if (mmrDiff <= 20) return "[PERFECT]";
        if (mmrDiff <= 50) return "[GREAT]  ";
        if (mmrDiff <= 100) return "[GOOD]   ";
        return "[FAIR]   ";
    }

    private static String getRoleBar(int count, int total) {
        int filled = count * 20 / total;
        return "[" + "█".repeat(filled) + "░".repeat(20 - filled) + "]";
    }

    private static String getProgressBar(int value, int total) {
        if (total == 0) return "[" + "░".repeat(20) + "]";
        int filled = value * 20 / total;
        return "[" + "█".repeat(filled) + "░".repeat(20 - filled) + "]";
    }

    private static String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private static RoleStats calculateRoleStats(Match match) {
        int primary = 0, secondary = 0, autofill = 0;

        for (PlayerAssignment a : match.team1().roster().values()) {
            switch (a.assignmentType()) {
                case PRIMARY -> primary++;
                case SECONDARY -> secondary++;
                case AUTOFILL -> autofill++;
            }
        }
        for (PlayerAssignment a : match.team2().roster().values()) {
            switch (a.assignmentType()) {
                case PRIMARY -> primary++;
                case SECONDARY -> secondary++;
                case AUTOFILL -> autofill++;
            }
        }

        return new RoleStats(primary, secondary, autofill);
    }

    private record RoleStats(int primary, int secondary, int autofill) {}
}
