package com.matchmaking.model;

public enum Rank {
    IRON(0, 499),
    BRONZE(500, 999),
    SILVER(1000, 1499),
    GOLD(1500, 1999),
    PLATINUM(2000, 2499),
    DIAMOND(2500, 2799),
    MASTER(2800, 2899),
    GRANDMASTER(2900, 2949),
    CHALLENGER(2950, Integer.MAX_VALUE);

    private final int minMmr;
    private final int maxMmr;

    Rank(int minMmr, int maxMmr) {
        this.minMmr = minMmr;
        this.maxMmr = maxMmr;
    }

    public int getMinMmr() {
        return minMmr;
    }

    public int getMaxMmr() {
        return maxMmr;
    }

    public static Rank fromMmr(int mmr) {
        for (Rank rank : values()) {
            if (mmr >= rank.minMmr && mmr <= rank.maxMmr) {
                return rank;
            }
        }
        return IRON;
    }
}
