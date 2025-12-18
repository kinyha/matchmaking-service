package com.matchmaking.config;

import com.matchmaking.algorithm.MmrWindowFinder;
import com.matchmaking.repository.InMemoryMatchRepository;
import com.matchmaking.repository.MatchRepository;
import com.matchmaking.repository.OptimizedQueueRepository;
import com.matchmaking.service.QueueService;
import com.matchmaking.service.RoleAssignmentService;
import com.matchmaking.service.TeamBalancerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public MatchmakingConfig matchmakingConfig() {
        return new MatchmakingConfig();
    }

    @Bean
    public OptimizedQueueRepository queueRepository() {
        return new OptimizedQueueRepository();
    }

    @Bean
    public MatchRepository matchRepository() {
        return new InMemoryMatchRepository();
    }

    @Bean
    public QueueService queueService(OptimizedQueueRepository queueRepository, Clock clock) {
        return new QueueService(queueRepository, clock);
    }

    @Bean
    public RoleAssignmentService roleAssignmentService() {
        return new RoleAssignmentService();
    }

    @Bean
    public TeamBalancerService teamBalancerService() {
        return new TeamBalancerService();
    }

    @Bean
    public MmrWindowFinder mmrWindowFinder() {
        return new MmrWindowFinder();
    }
}
