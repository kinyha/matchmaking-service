package com.matchmaking.dto;

import com.matchmaking.model.Match;
import com.matchmaking.model.PlayerAssignment;
import com.matchmaking.model.Role;
import com.matchmaking.model.Team;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record MatchResponse(
        String matchId,
        TeamResponse team1,
        TeamResponse team2,
        int avgMmr,
        int mmrDifference,
        Instant createdAt
) {
    public static MatchResponse from(Match match) {
        return new MatchResponse(
                match.id(),
                TeamResponse.from(match.team1()),
                TeamResponse.from(match.team2()),
                match.avgMmr(),
                match.mmrDifference(),
                match.createdAt()
        );
    }

    public record TeamResponse(
            List<PlayerResponse> players,
            int avgMmr,
            int avgEffectiveMmr
    ) {
        public static TeamResponse from(Team team) {
            List<PlayerResponse> players = team.roster().values().stream()
                    .map(PlayerResponse::from)
                    .collect(Collectors.toList());
            return new TeamResponse(players, team.avgMmr(), team.avgEffectiveMmr());
        }
    }

    public record PlayerResponse(
            String playerId,
            String displayName,
            int mmr,
            int effectiveMmr,
            Role assignedRole,
            String assignmentType
    ) {
        public static PlayerResponse from(PlayerAssignment assignment) {
            return new PlayerResponse(
                    assignment.player().id(),
                    assignment.player().displayName(),
                    assignment.player().mmr(),
                    assignment.effectiveMmr(),
                    assignment.assignedRole(),
                    assignment.assignmentType().name()
            );
        }
    }
}
