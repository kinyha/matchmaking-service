package com.matchmaking.dto;

import com.matchmaking.model.QueueEntry;
import com.matchmaking.model.Role;

import java.time.Instant;

public record QueueStatusResponse(
        String playerId,
        String displayName,
        int mmr,
        Role primaryRole,
        Role secondaryRole,
        Instant queueStartTime,
        long waitTimeSeconds
) {
    public static QueueStatusResponse from(QueueEntry entry, Instant now) {
        return new QueueStatusResponse(
                entry.player().id(),
                entry.player().displayName(),
                entry.player().mmr(),
                entry.player().primaryRole(),
                entry.player().secondaryRole(),
                entry.queueStartTime(),
                java.time.Duration.between(entry.queueStartTime(), now).toSeconds()
        );
    }
}
