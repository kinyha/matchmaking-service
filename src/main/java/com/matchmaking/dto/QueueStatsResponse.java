package com.matchmaking.dto;

import java.util.Map;

public record QueueStatsResponse(
        int totalPlayers,
        Map<Integer, Integer> mmrDistribution,
        Map<String, Integer> roleDistribution,
        double avgWaitTimeSeconds,
        long maxWaitTimeSeconds
) {}
