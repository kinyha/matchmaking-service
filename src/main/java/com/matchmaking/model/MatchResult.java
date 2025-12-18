package com.matchmaking.model;

import java.util.Optional;

public record MatchResult(
        boolean success,
        Match match,
        String failureReason
) {
    public static MatchResult ok(Match match) {
        return new MatchResult(true, match, null);
    }

    public static MatchResult fail(String reason) {
        return new MatchResult(false, null, reason);
    }

    public Optional<Match> getMatch() {
        return Optional.ofNullable(match);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }
}
