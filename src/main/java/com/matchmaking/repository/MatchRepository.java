package com.matchmaking.repository;

import com.matchmaking.model.Match;

import java.util.List;
import java.util.Optional;

public interface MatchRepository {
    void save(Match match);

    Optional<Match> findById(String matchId);

    List<Match> findAll();

    int count();
}
