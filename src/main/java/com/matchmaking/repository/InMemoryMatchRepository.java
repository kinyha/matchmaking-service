package com.matchmaking.repository;

import com.matchmaking.model.Match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMatchRepository implements MatchRepository {
    private final Map<String, Match> matches = new ConcurrentHashMap<>();

    @Override
    public void save(Match match) {
        matches.put(match.id(), match);
    }

    @Override
    public Optional<Match> findById(String matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    @Override
    public List<Match> findAll() {
        return new ArrayList<>(matches.values());
    }

    @Override
    public int count() {
        return matches.size();
    }
}
