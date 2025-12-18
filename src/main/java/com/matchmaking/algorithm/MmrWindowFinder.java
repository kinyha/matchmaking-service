package com.matchmaking.algorithm;

import com.matchmaking.model.Player;
import com.matchmaking.model.QueueEntry;
import com.matchmaking.model.Role;

import java.util.*;

public class MmrWindowFinder {

    public record WindowResult(
            List<QueueEntry> entries,
            int mmrSpread,
            int roleCoverageScore
    ) {}

    public Optional<WindowResult> findBestWindow(List<QueueEntry> allEntries, int windowSize) {
        if (allEntries.size() < windowSize) {
            return Optional.empty();
        }

        List<QueueEntry> sorted = new ArrayList<>(allEntries);
        sorted.sort(Comparator.comparingInt(e -> e.player().mmr()));

        WindowResult bestWindow = null;
        int bestScore = Integer.MAX_VALUE;

        for (int i = 0; i <= sorted.size() - windowSize; i++) {
            List<QueueEntry> window = sorted.subList(i, i + windowSize);
            int mmrSpread = calculateMmrSpread(window);
            int roleCoverage = calculateRoleCoverageScore(window);
            int score = mmrSpread + roleCoverage * 10;

            if (score < bestScore) {
                bestScore = score;
                bestWindow = new WindowResult(
                        new ArrayList<>(window),
                        mmrSpread,
                        roleCoverage
                );
            }
        }

        return Optional.ofNullable(bestWindow);
    }

    private int calculateMmrSpread(List<QueueEntry> window) {
        int minMmr = window.stream()
                .mapToInt(e -> e.player().mmr())
                .min()
                .orElse(0);
        int maxMmr = window.stream()
                .mapToInt(e -> e.player().mmr())
                .max()
                .orElse(0);
        return maxMmr - minMmr;
    }

    private int calculateRoleCoverageScore(List<QueueEntry> window) {
        Map<Role, Integer> primaryCount = new EnumMap<>(Role.class);
        Map<Role, Integer> secondaryCount = new EnumMap<>(Role.class);

        for (QueueEntry entry : window) {
            Player player = entry.player();
            primaryCount.merge(player.primaryRole(), 1, Integer::sum);
            secondaryCount.merge(player.secondaryRole(), 1, Integer::sum);
        }

        int missingRoles = 0;
        for (Role role : Role.values()) {
            int primary = primaryCount.getOrDefault(role, 0);
            int secondary = secondaryCount.getOrDefault(role, 0);
            int total = primary + secondary;
            if (total < 2) {
                missingRoles += (2 - total);
            }
        }
        return missingRoles;
    }
}
