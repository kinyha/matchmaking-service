# Matchmaking Service

Production-ready matchmaking system for 5v5 MOBA games. Built with Java 21 and Spring Boot.

## Features

- **Smart Role Assignment** - 3-pass algorithm (Primary → Secondary → Autofill)
- **Team Balancing** - Snake draft by effective MMR
- **MMR Window Selection** - Sliding window for optimal player grouping
- **High Performance** - 200+ matches/sec, O(1) MMR lookups with bucket indexing
- **Metrics & Monitoring** - Prometheus/Micrometer integration
- **REST API** - Full HTTP API for queue and matchmaking operations
- **Flexible Persistence** - In-memory or Redis storage

## Quick Start

```bash
# Run tests
./gradlew test

# Start application
./gradlew bootRun

# Application runs on http://localhost:8080
```

## API Endpoints

### Queue Management

```bash
# Add player to queue
curl -X POST http://localhost:8080/api/queue/enqueue \
  -H "Content-Type: application/json" \
  -d '{
    "playerId": "player123",
    "displayName": "ProGamer",
    "mmr": 1500,
    "primaryRole": "MID",
    "secondaryRole": "TOP"
  }'

# Remove player from queue
curl -X DELETE http://localhost:8080/api/queue/dequeue/player123

# Get player status
curl http://localhost:8080/api/queue/status/player123

# Get queue statistics
curl http://localhost:8080/api/queue/stats
```

### Matchmaking

```bash
# Create a match (requires 10+ players in queue)
curl -X POST http://localhost:8080/api/matchmaking/create

# Get all matches
curl http://localhost:8080/api/matchmaking/matches

# Get specific match
curl http://localhost:8080/api/matchmaking/match/{matchId}
```

### Monitoring

```bash
# Health check
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      REST Controllers                        │
│              QueueController │ MatchmakingController         │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                         Services                             │
│  QueueService │ RoleAssignmentService │ TeamBalancerService  │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                       Repositories                           │
│         OptimizedQueueRepository (MMR Buckets)               │
│              RedisQueueRepository (Optional)                 │
└─────────────────────────────────────────────────────────────┘
```

## Algorithms

### Role Assignment (3-Pass Greedy)

```
Pass 1: Assign PRIMARY roles (highest MMR first)
Pass 2: Assign SECONDARY roles for remaining players
Pass 3: AUTOFILL remaining players to open slots
```

### Team Balancing (Snake Draft)

```
1. Group players by role (2 per role)
2. Sort roles by MMR difference between players
3. For each role: assign higher MMR to team with lower total
```

### MMR Window Selection

```
1. Sort queue by MMR
2. Slide window of size 10
3. Score each window by: MMR spread + role coverage
4. Select window with best score
```

## Performance

Simulation results with 10,000 players:

| Metric | Result |
|--------|--------|
| Matches created | 1,000 |
| Match quality (MMR diff ≤20) | 99.7% |
| Average MMR difference | 8.2 |
| Primary role rate | 76.4% |
| Throughput | 218 matches/sec |
| Enqueue throughput | 500,000/sec |

## Configuration

```yaml
# application.yml
matchmaking:
  persistence: memory  # or 'redis'

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### Matchmaking Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `baseWindow` | 100 | Base MMR window (±100) |
| `maxWindow` | 500 | Maximum MMR window |
| `maxMmrDiff` | 100 | Max team MMR difference |
| `secondaryPenalty` | 50 | MMR penalty for secondary role |
| `autofillPenalty` | 100 | MMR penalty for autofill |

## Project Structure

```
src/main/java/com/matchmaking/
├── model/           # Domain models (Player, Match, Team, etc.)
├── service/         # Business logic
├── repository/      # Data access (In-memory, Redis)
├── controller/      # REST API
├── algorithm/       # MMR window finder, calculators
├── config/          # Spring configuration
├── dto/             # Request/Response DTOs
└── metrics/         # Micrometer metrics

src/test/java/com/matchmaking/
├── service/         # Unit tests
├── algorithm/       # Algorithm tests
├── integration/     # Integration tests
└── simulation/      # Load tests (10,000 players)
```

## Tech Stack

- **Java 21**
- **Spring Boot 3.4**
- **Micrometer** - Metrics
- **Redis** - Optional persistence
- **JUnit 5 + AssertJ** - Testing

## Metrics

Available at `/actuator/prometheus`:

```
# Matches
matchmaking_matches_created_total
matchmaking_matches_failed_total
matchmaking_match_mmr_difference{quantile="0.5|0.9|0.99"}
matchmaking_match_creation_time_seconds

# Queue
matchmaking_queue_size
matchmaking_queue_enqueued_total
matchmaking_queue_dequeued_total
matchmaking_queue_wait_time_seconds{quantile="0.5|0.9|0.99"}

# Role assignments
matchmaking_role_assignments_total{type="primary|secondary|autofill"}
```

## License

MIT
