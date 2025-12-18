package com.matchmaking.service;

import com.matchmaking.model.Player;
import com.matchmaking.model.QueueEntry;
import com.matchmaking.repository.QueueRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class QueueService {
    private final QueueRepository queueRepository;
    private final Clock clock;

    public QueueService(QueueRepository queueRepository) {
        this(queueRepository, Clock.systemUTC());
    }

    public QueueService(QueueRepository queueRepository, Clock clock) {
        this.queueRepository = queueRepository;
        this.clock = clock;
    }

    public QueueEntry enqueue(Player player) {
        if (queueRepository.contains(player.id())) {
            throw new IllegalStateException("Player " + player.id() + " is already in queue");
        }

        QueueEntry entry = QueueEntry.create(player, Instant.now(clock));
        queueRepository.add(entry);
        return entry;
    }

    public boolean dequeue(String playerId) {
        return queueRepository.remove(playerId);
    }

    public Optional<QueueEntry> getQueueStatus(String playerId) {
        return queueRepository.findById(playerId);
    }

    public int getQueueSize() {
        return queueRepository.size();
    }

    public List<QueueEntry> getAllEntries() {
        return queueRepository.findAll();
    }

    public boolean isInQueue(String playerId) {
        return queueRepository.contains(playerId);
    }

    public void removeAll(List<String> playerIds) {
        for (String playerId : playerIds) {
            queueRepository.remove(playerId);
        }
    }
}
