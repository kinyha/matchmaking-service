package com.matchmaking.controller;

import com.matchmaking.algorithm.MmrWindowFinder;
import com.matchmaking.config.MatchmakingConfig;
import com.matchmaking.dto.MatchResponse;
import com.matchmaking.metrics.MatchmakingMetrics;
import com.matchmaking.model.*;
import com.matchmaking.repository.MatchRepository;
import com.matchmaking.service.QueueService;
import com.matchmaking.service.RoleAssignmentService;
import com.matchmaking.service.TeamBalancerService;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {
    private final QueueService queueService;
    private final RoleAssignmentService roleAssignmentService;
    private final TeamBalancerService teamBalancerService;
    private final MmrWindowFinder mmrWindowFinder;
    private final MatchRepository matchRepository;
    private final MatchmakingConfig config;
    private final MatchmakingMetrics metrics;
    private final Clock clock;

    public MatchmakingController(QueueService queueService,
                                  RoleAssignmentService roleAssignmentService,
                                  TeamBalancerService teamBalancerService,
                                  MmrWindowFinder mmrWindowFinder,
                                  MatchRepository matchRepository,
                                  MatchmakingConfig config,
                                  MatchmakingMetrics metrics,
                                  Clock clock) {
        this.queueService = queueService;
        this.roleAssignmentService = roleAssignmentService;
        this.teamBalancerService = teamBalancerService;
        this.mmrWindowFinder = mmrWindowFinder;
        this.matchRepository = matchRepository;
        this.config = config;
        this.metrics = metrics;
        this.clock = clock;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createMatch() {
        Timer.Sample timerSample = metrics.startMatchCreationTimer();

        try {
            MatchResult result = tryCreateMatch();

            if (result.isSuccess()) {
                Match match = result.getMatch().get();
                metrics.recordMatchCreated(match);
                metrics.recordPlayersDequeued(10);

                // Record wait times
                Instant matchTime = match.createdAt();
                match.team1().roster().values().forEach(a ->
                        metrics.recordWaitTime(Instant.now(clock).minusSeconds(60), matchTime));
                match.team2().roster().values().forEach(a ->
                        metrics.recordWaitTime(Instant.now(clock).minusSeconds(60), matchTime));

                return ResponseEntity.ok(MatchResponse.from(match));
            } else {
                metrics.recordMatchFailed(result.failureReason());
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "reason", result.failureReason()
                ));
            }
        } finally {
            metrics.stopMatchCreationTimer(timerSample);
        }
    }

    @GetMapping("/match/{matchId}")
    public ResponseEntity<MatchResponse> getMatch(@PathVariable String matchId) {
        return matchRepository.findById(matchId)
                .map(match -> ResponseEntity.ok(MatchResponse.from(match)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/matches")
    public ResponseEntity<List<MatchResponse>> getAllMatches() {
        List<MatchResponse> matches = matchRepository.findAll().stream()
                .map(MatchResponse::from)
                .toList();
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/config")
    public ResponseEntity<MatchmakingConfig> getConfig() {
        return ResponseEntity.ok(config);
    }

    private MatchResult tryCreateMatch() {
        List<QueueEntry> allEntries = queueService.getAllEntries();

        if (allEntries.size() < config.getPlayersPerMatch()) {
            return MatchResult.fail("Not enough players in queue. Need " +
                    config.getPlayersPerMatch() + ", have " + allEntries.size());
        }

        Optional<MmrWindowFinder.WindowResult> windowOpt =
                mmrWindowFinder.findBestWindow(allEntries, config.getPlayersPerMatch());

        if (windowOpt.isEmpty()) {
            return MatchResult.fail("Could not find suitable player window");
        }

        MmrWindowFinder.WindowResult window = windowOpt.get();
        List<Player> players = window.entries().stream()
                .map(QueueEntry::player)
                .toList();

        Optional<List<PlayerAssignment>> assignmentsOpt = roleAssignmentService.assignRoles(players);

        if (assignmentsOpt.isEmpty()) {
            return MatchResult.fail("Could not assign roles to players");
        }

        List<PlayerAssignment> assignments = assignmentsOpt.get();
        TeamBalancerService.TeamBalanceResult teamResult = teamBalancerService.balanceTeams(assignments);

        if (teamResult.mmrDifference() > config.getMaxMmrDiff()) {
            return MatchResult.fail("MMR difference too high: " + teamResult.mmrDifference() +
                    " (max: " + config.getMaxMmrDiff() + ")");
        }

        Match match = Match.create(teamResult.team1(), teamResult.team2(), Instant.now(clock));
        matchRepository.save(match);

        List<String> playerIds = players.stream()
                .map(Player::id)
                .toList();
        queueService.removeAll(playerIds);

        return MatchResult.ok(match);
    }
}
