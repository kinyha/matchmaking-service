package com.matchmaking.metrics;

import com.matchmaking.model.AssignmentType;
import com.matchmaking.model.Match;
import com.matchmaking.model.PlayerAssignment;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MatchmakingMetrics {
    private final MeterRegistry registry;

    // Counters
    private final Counter matchesCreated;
    private final Counter matchesFailed;
    private final Counter playersEnqueued;
    private final Counter playersDequeued;

    // Gauges
    private final AtomicInteger queueSize = new AtomicInteger(0);

    // Distribution summaries
    private final DistributionSummary mmrDifference;
    private final DistributionSummary matchAvgMmr;
    private final DistributionSummary waitTime;

    // Timers
    private final Timer matchCreationTime;

    // Role assignment counters
    private final Counter primaryAssignments;
    private final Counter secondaryAssignments;
    private final Counter autofillAssignments;

    public MatchmakingMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Counters
        this.matchesCreated = Counter.builder("matchmaking.matches.created")
                .description("Total matches created")
                .register(registry);

        this.matchesFailed = Counter.builder("matchmaking.matches.failed")
                .description("Total failed match attempts")
                .register(registry);

        this.playersEnqueued = Counter.builder("matchmaking.queue.enqueued")
                .description("Total players enqueued")
                .register(registry);

        this.playersDequeued = Counter.builder("matchmaking.queue.dequeued")
                .description("Total players dequeued")
                .register(registry);

        // Gauges
        Gauge.builder("matchmaking.queue.size", queueSize, AtomicInteger::get)
                .description("Current queue size")
                .register(registry);

        // Distribution summaries
        this.mmrDifference = DistributionSummary.builder("matchmaking.match.mmr_difference")
                .description("MMR difference between teams")
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                .register(registry);

        this.matchAvgMmr = DistributionSummary.builder("matchmaking.match.avg_mmr")
                .description("Average MMR of matches")
                .register(registry);

        this.waitTime = DistributionSummary.builder("matchmaking.queue.wait_time_seconds")
                .description("Player wait time in queue")
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                .register(registry);

        // Timers
        this.matchCreationTime = Timer.builder("matchmaking.match.creation_time")
                .description("Time to create a match")
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                .register(registry);

        // Role assignments
        this.primaryAssignments = Counter.builder("matchmaking.role.assignments")
                .tag("type", "primary")
                .description("Primary role assignments")
                .register(registry);

        this.secondaryAssignments = Counter.builder("matchmaking.role.assignments")
                .tag("type", "secondary")
                .description("Secondary role assignments")
                .register(registry);

        this.autofillAssignments = Counter.builder("matchmaking.role.assignments")
                .tag("type", "autofill")
                .description("Autofill role assignments")
                .register(registry);
    }

    public void recordMatchCreated(Match match) {
        matchesCreated.increment();
        mmrDifference.record(match.mmrDifference());
        matchAvgMmr.record(match.avgMmr());

        // Record role assignments
        recordRoleAssignments(match);
    }

    public void recordMatchFailed(String reason) {
        matchesFailed.increment();
        Counter.builder("matchmaking.matches.failed.reason")
                .tag("reason", sanitizeReason(reason))
                .register(registry)
                .increment();
    }

    public void recordPlayerEnqueued() {
        playersEnqueued.increment();
        queueSize.incrementAndGet();
    }

    public void recordPlayerDequeued() {
        playersDequeued.increment();
        queueSize.decrementAndGet();
    }

    public void recordPlayersDequeued(int count) {
        for (int i = 0; i < count; i++) {
            playersDequeued.increment();
            queueSize.decrementAndGet();
        }
    }

    public void recordWaitTime(Instant queueStartTime, Instant matchTime) {
        Duration duration = Duration.between(queueStartTime, matchTime);
        waitTime.record(duration.toSeconds());
    }

    public Timer.Sample startMatchCreationTimer() {
        return Timer.start(registry);
    }

    public void stopMatchCreationTimer(Timer.Sample sample) {
        sample.stop(matchCreationTime);
    }

    public void setQueueSize(int size) {
        queueSize.set(size);
    }

    private void recordRoleAssignments(Match match) {
        match.team1().roster().values().forEach(this::recordAssignment);
        match.team2().roster().values().forEach(this::recordAssignment);
    }

    private void recordAssignment(PlayerAssignment assignment) {
        switch (assignment.assignmentType()) {
            case PRIMARY -> primaryAssignments.increment();
            case SECONDARY -> secondaryAssignments.increment();
            case AUTOFILL -> autofillAssignments.increment();
        }
    }

    private String sanitizeReason(String reason) {
        if (reason == null) return "unknown";
        if (reason.contains("Not enough players")) return "not_enough_players";
        if (reason.contains("MMR difference")) return "mmr_too_high";
        if (reason.contains("role")) return "role_assignment_failed";
        return "other";
    }
}
