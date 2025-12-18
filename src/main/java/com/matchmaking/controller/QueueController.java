package com.matchmaking.controller;

import com.matchmaking.dto.EnqueueRequest;
import com.matchmaking.dto.QueueStatusResponse;
import com.matchmaking.dto.QueueStatsResponse;
import com.matchmaking.metrics.MatchmakingMetrics;
import com.matchmaking.model.Player;
import com.matchmaking.model.QueueEntry;
import com.matchmaking.model.Role;
import com.matchmaking.repository.QueueRepository;
import com.matchmaking.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/queue")
public class QueueController {
    private final QueueService queueService;
    private final QueueRepository queueRepository;
    private final MatchmakingMetrics metrics;
    private final Clock clock;

    public QueueController(QueueService queueService,
                           QueueRepository queueRepository,
                           MatchmakingMetrics metrics,
                           Clock clock) {
        this.queueService = queueService;
        this.queueRepository = queueRepository;
        this.metrics = metrics;
        this.clock = clock;
    }

    @PostMapping("/enqueue")
    public ResponseEntity<QueueStatusResponse> enqueue(@Valid @RequestBody EnqueueRequest request) {
        if (request.primaryRole() == request.secondaryRole()) {
            return ResponseEntity.badRequest().build();
        }

        Player player = Player.create(
                request.playerId(),
                request.displayName(),
                request.mmr(),
                request.primaryRole(),
                request.secondaryRole()
        );

        try {
            QueueEntry entry = queueService.enqueue(player);
            metrics.recordPlayerEnqueued();
            return ResponseEntity.ok(QueueStatusResponse.from(entry, Instant.now(clock)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/dequeue/{playerId}")
    public ResponseEntity<Void> dequeue(@PathVariable String playerId) {
        boolean removed = queueService.dequeue(playerId);
        if (removed) {
            metrics.recordPlayerDequeued();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/status/{playerId}")
    public ResponseEntity<QueueStatusResponse> getStatus(@PathVariable String playerId) {
        return queueService.getQueueStatus(playerId)
                .map(entry -> ResponseEntity.ok(QueueStatusResponse.from(entry, Instant.now(clock))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/size")
    public ResponseEntity<Map<String, Integer>> getQueueSize() {
        return ResponseEntity.ok(Map.of("size", queueService.getQueueSize()));
    }

    @GetMapping("/stats")
    public ResponseEntity<QueueStatsResponse> getStats() {
        List<QueueEntry> entries = queueService.getAllEntries();
        Instant now = Instant.now(clock);

        // Calculate role distribution
        Map<String, Integer> roleDistribution = new EnumMap<>(Role.class)
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().name(),
                        e -> 0
                ));

        Map<String, Integer> primaryRoles = new java.util.HashMap<>();
        for (Role role : Role.values()) {
            primaryRoles.put(role.name(), 0);
        }

        for (QueueEntry entry : entries) {
            String role = entry.player().primaryRole().name();
            primaryRoles.merge(role, 1, Integer::sum);
        }

        // Calculate wait times
        double avgWaitTime = entries.stream()
                .mapToLong(e -> Duration.between(e.queueStartTime(), now).toSeconds())
                .average()
                .orElse(0);

        long maxWaitTime = entries.stream()
                .mapToLong(e -> Duration.between(e.queueStartTime(), now).toSeconds())
                .max()
                .orElse(0);

        QueueStatsResponse response = new QueueStatsResponse(
                entries.size(),
                queueRepository.getBucketDistribution(),
                primaryRoles,
                avgWaitTime,
                maxWaitTime
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/players")
    public ResponseEntity<List<QueueStatusResponse>> getAllPlayers() {
        Instant now = Instant.now(clock);
        List<QueueStatusResponse> players = queueService.getAllEntries().stream()
                .map(entry -> QueueStatusResponse.from(entry, now))
                .toList();
        return ResponseEntity.ok(players);
    }
}
