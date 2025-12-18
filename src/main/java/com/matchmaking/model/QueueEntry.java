package com.matchmaking.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Objects;

public record QueueEntry(
        Player player,
        Instant queueStartTime
) {
    public QueueEntry {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(queueStartTime, "queueStartTime must not be null");
    }

    public static QueueEntry create(Player player, Instant queueStartTime) {
        return new QueueEntry(player, queueStartTime);
    }

    @JsonIgnore
    public String getPlayerId() {
        return player.id();
    }
}
