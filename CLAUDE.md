# Matchmaking Service — Claude Code Guide

## Project Goal

Создать production-ready систему матчмейкинга для MOBA 5v5.
Приоритеты: корректность > качество матчей > производительность.

---

## Current Phase

**Phase 1: MVP** — базовый матчмейкинг

---

## Implementation Checklist

### Phase 1: MVP
- [x] Models: Player, Role, Match, Team, PlayerAssignment
- [ ] QueueService: enqueue/dequeue с защитой от дубликатов
- [ ] MatchmakingService.tryCreateMatch():
  - [ ] Сортировка по MMR
  - [ ] Выбор 10 ближайших игроков (sliding window)
  - [ ] Role assignment (primary → secondary → autofill)
  - [ ] Team balancing (minimize MMR diff)
- [ ] Unit tests: 80%+ coverage
- [ ] Integration test: full flow

### Phase 2: Role System
- [ ] Effective MMR (primary +0, secondary -50, autofill -100)
- [ ] Autofill protection flag
- [ ] Role shortage detection

### Phase 3: Time-Based Expansion
- [ ] MMR window расширяется со временем
- [ ] Priority queue по wait time
- [ ] "Anchor" player logic

### Phase 4: Duo Queue
- [ ] PartyService: create/dissolve
- [ ] Duo restrictions (MMR gap, rank gap)
- [ ] Duo always same team

---

## Code Style

```java
// Records для immutable data
record Player(String id, int mmr, Role primary, Role secondary) {}

// Factory methods для результатов
record MatchResult(boolean found, Match match, String reason) {
    static MatchResult ok(Match m) { ... }
    static MatchResult fail(String reason) { ... }
}

// Service pattern с DI
class MatchmakingService {
    private final QueueRepository queue;
    private final MatchmakingConfig config;

    public MatchmakingService(QueueRepository queue, MatchmakingConfig config) {
        this.queue = queue;
        this.config = config;
    }
}
```

---

## Key Algorithms

### Role Assignment (Greedy)
```
1. Pass 1: Assign all PRIMARY roles
2. Pass 2: Fill remaining with SECONDARY
3. Pass 3: Fill remaining with AUTOFILL
4. Validate: each role has exactly 2 players
```

### Team Balancing
```
1. Sort 10 players by effective MMR descending
2. Alternate: [0]→T1, [1]→T2, [2]→T1, [3]→T2, ...
   OR
   Better: [0]→T1, [1]→T2, [2]→T2, [3]→T1, [4]→T1, [5]→T2...
   (snake draft)
```

### MMR Window Selection
```
1. Sort queue by MMR
2. Slide window of size 10
3. Score each window: spread + role coverage
4. Pick best window
```

---

## Testing Strategy

```
Unit Tests:
- RoleAssignmentServiceTest: all combinations
- TeamBalancerTest: optimal split
- MmrWindowFinderTest: edge cases

Integration Tests:
- Full matchmaking with 30 players
- Duo queue scenarios
- Edge: not enough supports

Simulation:
- Generate 10,000 players
- Run 1,000 matches
- Analyze: MMR diff distribution, role satisfaction
```

---

## Edge Cases to Handle

| Case | Expected Behavior |
|------|-------------------|
| <10 players | Return empty, wait |
| All same role (10 MID) | Autofill 8 players |
| Huge MMR spread (500 vs 2500) | Reject, wait for more |
| Player leaves during matching | Rollback, re-queue others |
| Duo with conflicting roles | Reject party creation |

---

## Config Defaults

```java
class MatchmakingConfig {
    int baseWindow = 100;           // ±100 MMR
    int maxWindow = 500;            // ±500 MMR after 5 min
    int windowExpansionPerMin = 50; // +50 per minute

    int secondaryPenalty = 50;      // -50 effective MMR
    int autofillPenalty = 100;      // -100 effective MMR

    int maxMmrDiff = 100;           // reject match if diff > 100
    int maxAutofillPerTeam = 1;     // max 1 autofill per team

    int maxDuoMmrGap = 500;         // duo can't have >500 MMR diff
}
```

---

## Don't Do

- ❌ Hardcode player lists — use repository
- ❌ Random team assignment — always optimize
- ❌ Ignore secondary roles — use all available info
- ❌ Skip validation — check role uniqueness, team size
- ❌ Mutable shared state — queue operations should be atomic

---

## Do

- ✅ Test edge cases first
- ✅ Log match quality metrics
- ✅ Use Clock injection for time-based logic
- ✅ Return detailed failure reasons
- ✅ Keep matching algorithm pluggable (strategy pattern)

---

## File Locations

```
Models:      src/main/java/.../model/
Services:    src/main/java/.../service/
Algorithms:  src/main/java/.../algorithm/
Tests:       src/test/java/.../
Spec:        PROJECT_SPEC.md (this folder)
```

---

## Quick Commands

```bash
# Run tests
./gradlew test --tests "*matchmaking*"

# Run specific test
./gradlew test --tests "MatchMakingTest.canCreateMatch"

# Run with logging
./gradlew test --info
```

---

## Next Steps

1. Implement `QueueService.enqueue()` with duplicate check
2. Implement `findCandidates()` — sliding window by MMR
3. Implement `assignRoles()` — 3-pass algorithm
4. Implement `balanceTeams()` — snake draft
5. Wire together in `tryCreateMatch()`
6. Add tests for each step
