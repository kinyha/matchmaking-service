# Matchmaking Service — Full Project Specification

## Overview

Разработать систему подбора матчей для соревновательной игры 5v5 (MOBA).
Система должна балансировать качество матча, время ожидания и справедливость распределения ролей.

---

## Core Entities

### Player
```
- id: String (unique)
- displayName: String
- mmr: int (0-3000) — скрытый рейтинг
- rank: Rank (IRON..CHALLENGER) — видимый ранг
- primaryRole: Role
- secondaryRole: Role
- autofillProtected: boolean — защита от autofill
- recentMatches: List<MatchHistory> — последние N матчей
```

### Role
```
TOP, JUNGLE, MID, ADC, SUPPORT
```

### Rank (видимый ранг, зависит от MMR)
```
IRON (0-499), BRONZE (500-999), SILVER (1000-1499),
GOLD (1500-1999), PLATINUM (2000-2499), DIAMOND (2500-2799),
MASTER (2800-2899), GRANDMASTER (2900-2949), CHALLENGER (2950+)
```

### QueueEntry
```
- player: Player
- queueStartTime: Instant
- partyId: String? — для duo queue
- currentMmrTolerance: int — расширяется со временем
```

### Match
```
- id: String
- team1: Team
- team2: Team
- avgMmr: int
- mmrDifference: int
- createdAt: Instant
- estimatedQuality: double (0-1)
```

### Team
```
- roster: Map<Role, PlayerAssignment>
- avgMmr: int
- avgEffectiveMmr: int (с учётом role penalty)
```

### PlayerAssignment
```
- player: Player
- assignedRole: Role
- assignmentType: PRIMARY | SECONDARY | AUTOFILL
- effectiveMmr: int
```

---

## Functional Requirements

### Phase 1: Core Matchmaking (MVP)

**1.1 Queue Management**
- [ ] `enqueue(Player player)` — добавить игрока в очередь
- [ ] `dequeue(String playerId)` — убрать игрока из очереди
- [ ] `getQueueStatus(String playerId)` — статус в очереди (позиция, время)
- [ ] Защита от дубликатов (игрок не может быть в очереди дважды)

**1.2 Basic Matching**
- [ ] Найти 10 игроков с близким MMR
- [ ] Каждый игрок получает уникальную роль в команде
- [ ] Приоритет: primary > secondary > autofill
- [ ] Разделить на 2 команды с минимальной разницей MMR

**1.3 Match Quality Metrics**
- [ ] MMR difference между командами (target: ≤50)
- [ ] Primary role % (target: ≥80%)
- [ ] Autofill count per team (target: ≤1)

---

### Phase 2: Advanced Role Assignment

**2.1 Role Priority System**
- [ ] Effective MMR: primary +0, secondary -50, autofill -100
- [ ] При расчёте баланса использовать effective MMR
- [ ] Предпочитать матчи где все на primary ролях

**2.2 Autofill Protection**
- [ ] После autofill игрок получает protection на следующий матч
- [ ] Protected игрок гарантированно получает primary/secondary
- [ ] Tracking autofill history per player

**2.3 Role Popularity Handling**
- [ ] Отслеживать дефицит ролей (обычно SUPPORT)
- [ ] Fill bonus: бонус к LP/MMR за игру на редкой роли
- [ ] Показывать estimated queue time по ролям

---

### Phase 3: MMR Window & Time-Based Expansion

**3.1 Sliding Window**
- [ ] Искать 10 игроков в MMR window (не любых 10)
- [ ] Window size зависит от времени в очереди
- [ ] Базовое окно: ±100 MMR

**3.2 Time-Based Expansion**
```
0-60 sec:   ±100 MMR (strict)
60-120 sec: ±150 MMR
120-180 sec: ±200 MMR
180-300 sec: ±300 MMR
300+ sec:    ±500 MMR (desperation mode)
```

**3.3 Priority Queue**
- [ ] Игроки с долгим ожиданием имеют приоритет
- [ ] Long-wait player "притягивает" матч к своему MMR

---

### Phase 4: Duo Queue

**4.1 Party Management**
- [ ] `createParty(player1, player2)` — создать duo
- [ ] `dissolveParty(partyId)` — распустить duo
- [ ] Duo всегда попадает в одну команду

**4.2 Duo Restrictions**
- [ ] Max MMR difference в duo: 500 (нельзя Bronze + Diamond)
- [ ] Max rank difference: 2 tiers (Gold может с Plat, не с Diamond)
- [ ] Duo MMR = average + penalty (+50 для баланса)

**4.3 Duo Role Selection**
- [ ] Каждый игрок duo выбирает 2 роли
- [ ] Роли не должны конфликтовать (оба MID/MID — ошибка)

---

### Phase 5: Ranked Restrictions

**5.1 Rank-Based Matching**
- [ ] Игроки разных tier'ов не могут быть в одном матче
- [ ] Iron-Gold: могут вместе
- [ ] Platinum-Diamond: могут вместе
- [ ] Master+: отдельный пул

**5.2 Promotion/Demotion Series**
- [ ] Игрок на промо получает небольшой MMR boost для "проверки"
- [ ] Demotion shield после повышения

**5.3 Smurf Detection (опционально)
- [ ] Высокий winrate (>65%) на низком ранге
- [ ] Ускоренный MMR gain для смурфов
- [ ] Пометка в матче "возможно смурф"

---

### Phase 6: Match History & Dodge Prevention

**6.1 Recent Opponents**
- [ ] Не ставить в одну команду игроков которые недавно репортили друг друга
- [ ] Avoid list (до 5 игроков) — не попадать с ними в команду
- [ ] Cooldown на повторный матч с теми же игроками (5 минут)

**6.2 Dodge Penalty**
- [ ] При dodge (выход из lobby) — penalty к queue time
- [ ] Repeat dodge — временный ban

---

## Non-Functional Requirements

### Performance
- [ ] Find match for 10 players: <100ms
- [ ] Support 10,000 concurrent players in queue
- [ ] Queue update latency: <1 sec

### Observability
- [ ] Metrics: queue size, avg wait time, match quality distribution
- [ ] Logs: match creation events, queue events
- [ ] Alerts: if avg wait time > 5 min

### Testing
- [ ] Unit tests: role assignment, team balancing, MMR calculation
- [ ] Integration tests: full matchmaking flow
- [ ] Load tests: 10k concurrent queue entries
- [ ] Simulation: run 1000 matches, analyze quality distribution

---

## Technical Design

### Data Structures

**Queue Storage**
```java
// По MMR buckets для быстрого поиска
Map<Integer, List<QueueEntry>> mmrBuckets; // bucket = mmr / 100

// По времени для priority
PriorityQueue<QueueEntry> byWaitTime;

// Быстрый lookup
Map<String, QueueEntry> byPlayerId;
```

**Matching Algorithm**
```
1. Выбрать "anchor" игрока (longest wait time)
2. Найти кандидатов в его MMR window
3. Попробовать собрать 10 игроков с валидным role assignment
4. Разделить на команды с минимальной MMR diff
5. Если не получилось — расширить window, повторить
```

**Role Assignment (Hungarian Algorithm или Greedy)**
```
1. Build cost matrix: player × role
   - Cost = 0 if primary, 50 if secondary, 100 if autofill
   - Cost = INF if role already taken
2. Find minimum cost assignment
3. Validate: каждая роль ровно 2 раза (1 per team)
```

**Team Balancing**
```
1. Sort 10 players by effective MMR
2. Greedy assignment: alternate teams
3. Or: try all C(10,5) = 252 combinations, pick best
```

---

## Project Structure

```
matchmaking-service/
├── src/main/java/
│   ├── model/
│   │   ├── Player.java
│   │   ├── Role.java
│   │   ├── Rank.java
│   │   ├── QueueEntry.java
│   │   ├── Match.java
│   │   ├── Team.java
│   │   └── PlayerAssignment.java
│   │
│   ├── service/
│   │   ├── QueueService.java          // enqueue, dequeue
│   │   ├── MatchmakingService.java    // core matching logic
│   │   ├── RoleAssignmentService.java // role distribution
│   │   ├── TeamBalancer.java          // split into teams
│   │   └── PartyService.java          // duo queue
│   │
│   ├── algorithm/
│   │   ├── MmrWindowFinder.java       // sliding window
│   │   ├── HungarianAlgorithm.java    // optimal role assignment
│   │   └── GreedyRoleAssigner.java    // simple fallback
│   │
│   ├── repository/
│   │   ├── PlayerRepository.java
│   │   ├── QueueRepository.java
│   │   └── MatchRepository.java
│   │
│   └── config/
│       └── MatchmakingConfig.java     // thresholds, timeouts
│
├── src/test/java/
│   ├── unit/
│   ├── integration/
│   └── simulation/
│       └── MatchmakingSimulation.java // run 1000 matches
│
└── docs/
    ├── ARCHITECTURE.md
    └── ALGORITHM.md
```

---

## Implementation Phases

### Phase 1: MVP (2-3 дня)
```
□ Models: Player, Role, QueueEntry, Match, Team
□ QueueService: enqueue/dequeue
□ Basic matching: first 10 by MMR
□ Role assignment: primary only
□ Team split: alternating by MMR
□ Unit tests
```

### Phase 2: Role System (2 дня)
```
□ Secondary role support
□ Autofill with protection
□ Effective MMR calculation
□ Role assignment tests
```

### Phase 3: MMR Window (1-2 дня)
```
□ Sliding window implementation
□ Time-based expansion
□ Priority by wait time
□ Integration tests
```

### Phase 4: Duo Queue (2 дня)
```
□ Party model
□ Duo restrictions
□ Duo in same team
□ Edge cases tests
```

### Phase 5: Advanced (3+ дня)
```
□ Rank restrictions
□ Match history / avoid list
□ Dodge penalty
□ Metrics & observability
□ Load testing
□ Simulation with 10k players
```

---

## Success Criteria

| Metric | Target | Измерение |
|--------|--------|-----------|
| Avg MMR diff | ≤50 | Per match |
| Primary role rate | ≥80% | Per player |
| Autofill rate | ≤10% | Per player |
| Avg queue time | ≤2 min | 90th percentile |
| Max queue time | ≤5 min | 99th percentile |
| Match creation time | ≤100ms | P99 latency |

---

## Open Questions

1. Как обрабатывать пустую очередь определённой роли ночью?
2. Нужен ли "fill" как primary role (любая роль)?
3. Как балансировать premade vs solo (5-stack advantage)?
4. Regional sharding или global queue?
5. Voice chat matching (optional)?

---

## References

- [Riot: /dev diary on matchmaking](https://www.leagueoflegends.com/en-us/news/dev/)
- [Elo rating system](https://en.wikipedia.org/wiki/Elo_rating_system)
- [TrueSkill (Microsoft)](https://en.wikipedia.org/wiki/TrueSkill)
- [Hungarian Algorithm](https://en.wikipedia.org/wiki/Hungarian_algorithm)
