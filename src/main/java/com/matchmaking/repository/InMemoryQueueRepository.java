package com.matchmaking.repository;

import com.matchmaking.model.QueueEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQueueRepository implements QueueRepository {
    private final Map<String, QueueEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void add(QueueEntry entry) {
        entries.put(entry.getPlayerId(), entry);
    }

    @Override
    public boolean remove(String playerId) {
        return entries.remove(playerId) != null;
    }

    @Override
    public Optional<QueueEntry> findById(String playerId) {
        return Optional.ofNullable(entries.get(playerId));
    }

    @Override
    public List<QueueEntry> findAll() {
        return new ArrayList<>(entries.values());
    }

    @Override
    public boolean contains(String playerId) {
        return entries.containsKey(playerId);
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
    }
}
