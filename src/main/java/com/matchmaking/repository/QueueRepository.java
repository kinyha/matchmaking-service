package com.matchmaking.repository;

import com.matchmaking.model.QueueEntry;

import java.util.List;
import java.util.Optional;

public interface QueueRepository {
    void add(QueueEntry entry);

    boolean remove(String playerId);

    Optional<QueueEntry> findById(String playerId);

    List<QueueEntry> findAll();

    boolean contains(String playerId);

    int size();

    void clear();
}
