package com.matchmaking.service;

import com.matchmaking.model.Player;
import com.matchmaking.model.QueueEntry;
import com.matchmaking.model.Role;
import com.matchmaking.repository.InMemoryQueueRepository;
import com.matchmaking.repository.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class QueueServiceTest {
    private QueueRepository queueRepository;
    private QueueService queueService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        queueRepository = new InMemoryQueueRepository();
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        queueService = new QueueService(queueRepository, fixedClock);
    }

    @Test
    void enqueue_addsPlayerToQueue() {
        Player player = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);

        QueueEntry entry = queueService.enqueue(player);

        assertThat(entry.player()).isEqualTo(player);
        assertThat(entry.queueStartTime()).isEqualTo(Instant.parse("2024-01-01T12:00:00Z"));
        assertThat(queueService.getQueueSize()).isEqualTo(1);
    }

    @Test
    void enqueue_throwsException_whenPlayerAlreadyInQueue() {
        Player player = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);
        queueService.enqueue(player);

        assertThatThrownBy(() -> queueService.enqueue(player))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in queue");
    }

    @Test
    void dequeue_removesPlayerFromQueue() {
        Player player = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);
        queueService.enqueue(player);

        boolean removed = queueService.dequeue("p1");

        assertThat(removed).isTrue();
        assertThat(queueService.getQueueSize()).isEqualTo(0);
    }

    @Test
    void dequeue_returnsFalse_whenPlayerNotInQueue() {
        boolean removed = queueService.dequeue("nonexistent");

        assertThat(removed).isFalse();
    }

    @Test
    void getQueueStatus_returnsEntry_whenPlayerInQueue() {
        Player player = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);
        queueService.enqueue(player);

        Optional<QueueEntry> status = queueService.getQueueStatus("p1");

        assertThat(status).isPresent();
        assertThat(status.get().player()).isEqualTo(player);
    }

    @Test
    void getQueueStatus_returnsEmpty_whenPlayerNotInQueue() {
        Optional<QueueEntry> status = queueService.getQueueStatus("nonexistent");

        assertThat(status).isEmpty();
    }

    @Test
    void getAllEntries_returnsAllQueuedPlayers() {
        Player player1 = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);
        Player player2 = Player.create("p2", "Player2", 1600, Role.ADC, Role.SUPPORT);

        queueService.enqueue(player1);
        queueService.enqueue(player2);

        List<QueueEntry> entries = queueService.getAllEntries();

        assertThat(entries).hasSize(2);
    }

    @Test
    void isInQueue_returnsTrue_whenPlayerInQueue() {
        Player player = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);
        queueService.enqueue(player);

        assertThat(queueService.isInQueue("p1")).isTrue();
    }

    @Test
    void isInQueue_returnsFalse_whenPlayerNotInQueue() {
        assertThat(queueService.isInQueue("nonexistent")).isFalse();
    }

    @Test
    void removeAll_removesMultiplePlayers() {
        Player player1 = Player.create("p1", "Player1", 1500, Role.MID, Role.TOP);
        Player player2 = Player.create("p2", "Player2", 1600, Role.ADC, Role.SUPPORT);
        Player player3 = Player.create("p3", "Player3", 1700, Role.JUNGLE, Role.TOP);

        queueService.enqueue(player1);
        queueService.enqueue(player2);
        queueService.enqueue(player3);

        queueService.removeAll(List.of("p1", "p2"));

        assertThat(queueService.getQueueSize()).isEqualTo(1);
        assertThat(queueService.isInQueue("p3")).isTrue();
    }
}
