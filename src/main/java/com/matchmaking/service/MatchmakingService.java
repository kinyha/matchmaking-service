package com.matchmaking.service;

import com.matchmaking.algorithm.MmrWindowFinder;
import com.matchmaking.config.MatchmakingConfig;
import com.matchmaking.model.*;
import com.matchmaking.repository.MatchRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class MatchmakingService {
    private final QueueService queueService;
    private final RoleAssignmentService roleAssignmentService;
    private final TeamBalancerService teamBalancerService;
    private final MmrWindowFinder mmrWindowFinder;
    private final MatchRepository matchRepository;
    private final MatchmakingConfig config;
    private final Clock clock;

    public MatchmakingService(
            QueueService queueService,
            RoleAssignmentService roleAssignmentService,
            TeamBalancerService teamBalancerService,
            MmrWindowFinder mmrWindowFinder,
            MatchRepository matchRepository,
            MatchmakingConfig config) {
        this(queueService, roleAssignmentService, teamBalancerService,
             mmrWindowFinder, matchRepository, config, Clock.systemUTC());
    }

    public MatchmakingService(
            QueueService queueService,
            RoleAssignmentService roleAssignmentService,
            TeamBalancerService teamBalancerService,
            MmrWindowFinder mmrWindowFinder,
            MatchRepository matchRepository,
            MatchmakingConfig config,
            Clock clock) {
        this.queueService = queueService;
        this.roleAssignmentService = roleAssignmentService;
        this.teamBalancerService = teamBalancerService;
        this.mmrWindowFinder = mmrWindowFinder;
        this.matchRepository = matchRepository;
        this.config = config;
        this.clock = clock;
    }

    public MatchResult tryCreateMatch() {
        List<QueueEntry> allEntries = queueService.getAllEntries();

        if (allEntries.size() < config.getPlayersPerMatch()) {
            return MatchResult.fail("Not enough players in queue. Need " +
                    config.getPlayersPerMatch() + ", have " + allEntries.size());
        }

        // Find best window of 10 players
        Optional<MmrWindowFinder.WindowResult> windowOpt =
                mmrWindowFinder.findBestWindow(allEntries, config.getPlayersPerMatch());

        if (windowOpt.isEmpty()) {
            return MatchResult.fail("Could not find suitable player window");
        }

        MmrWindowFinder.WindowResult window = windowOpt.get();
        List<Player> players = window.entries().stream()
                .map(QueueEntry::player)
                .toList();

        // Assign roles
        Optional<List<PlayerAssignment>> assignmentsOpt = roleAssignmentService.assignRoles(players);

        if (assignmentsOpt.isEmpty()) {
            return MatchResult.fail("Could not assign roles to players");
        }

        List<PlayerAssignment> assignments = assignmentsOpt.get();

        // Balance teams
        TeamBalancerService.TeamBalanceResult teamResult = teamBalancerService.balanceTeams(assignments);

        // Check MMR difference threshold
        if (teamResult.mmrDifference() > config.getMaxMmrDiff()) {
            return MatchResult.fail("MMR difference too high: " + teamResult.mmrDifference() +
                    " (max: " + config.getMaxMmrDiff() + ")");
        }

        // Create match
        Match match = Match.create(teamResult.team1(), teamResult.team2(), Instant.now(clock));
        matchRepository.save(match);

        // Remove players from queue
        List<String> playerIds = players.stream()
                .map(Player::id)
                .toList();
        queueService.removeAll(playerIds);

        return MatchResult.ok(match);
    }

    public int getQueueSize() {
        return queueService.getQueueSize();
    }
}
