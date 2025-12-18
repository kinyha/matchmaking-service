package com.matchmaking.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.matchmaking.model.QueueEntry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-backed queue repository with:
 * - Hash for player data storage
 * - Sorted Set for MMR-based ordering
 * - Sorted Set for wait time ordering
 */
public class RedisQueueRepository implements QueueRepository {
    private static final String QUEUE_HASH_KEY = "matchmaking:queue:players";
    private static final String MMR_ZSET_KEY = "matchmaking:queue:mmr";
    private static final String WAIT_ZSET_KEY = "matchmaking:queue:waittime";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisQueueRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void add(QueueEntry entry) {
        String playerId = entry.getPlayerId();
        String json = serialize(entry);

        // Store in hash
        redisTemplate.opsForHash().put(QUEUE_HASH_KEY, playerId, json);

        // Add to MMR sorted set (score = MMR)
        redisTemplate.opsForZSet().add(MMR_ZSET_KEY, playerId, entry.player().mmr());

        // Add to wait time sorted set (score = timestamp millis)
        redisTemplate.opsForZSet().add(WAIT_ZSET_KEY, playerId,
                entry.queueStartTime().toEpochMilli());
    }

    @Override
    public boolean remove(String playerId) {
        Long removed = redisTemplate.opsForHash().delete(QUEUE_HASH_KEY, playerId);
        redisTemplate.opsForZSet().remove(MMR_ZSET_KEY, playerId);
        redisTemplate.opsForZSet().remove(WAIT_ZSET_KEY, playerId);
        return removed != null && removed > 0;
    }

    @Override
    public Optional<QueueEntry> findById(String playerId) {
        Object json = redisTemplate.opsForHash().get(QUEUE_HASH_KEY, playerId);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize((String) json));
    }

    @Override
    public List<QueueEntry> findAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(QUEUE_HASH_KEY);
        return entries.values().stream()
                .map(json -> deserialize((String) json))
                .collect(Collectors.toList());
    }

    @Override
    public boolean contains(String playerId) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(QUEUE_HASH_KEY, playerId));
    }

    @Override
    public int size() {
        Long size = redisTemplate.opsForHash().size(QUEUE_HASH_KEY);
        return size != null ? size.intValue() : 0;
    }

    @Override
    public void clear() {
        redisTemplate.delete(QUEUE_HASH_KEY);
        redisTemplate.delete(MMR_ZSET_KEY);
        redisTemplate.delete(WAIT_ZSET_KEY);
    }

    /**
     * Find entries within MMR range using Redis sorted set
     */
    public List<QueueEntry> findByMmrRange(int minMmr, int maxMmr) {
        Set<String> playerIds = redisTemplate.opsForZSet()
                .rangeByScore(MMR_ZSET_KEY, minMmr, maxMmr);

        if (playerIds == null || playerIds.isEmpty()) {
            return Collections.emptyList();
        }

        return playerIds.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Get entries ordered by wait time (oldest first)
     */
    public List<QueueEntry> findAllByWaitTimePriority() {
        Set<String> playerIds = redisTemplate.opsForZSet().range(WAIT_ZSET_KEY, 0, -1);

        if (playerIds == null || playerIds.isEmpty()) {
            return Collections.emptyList();
        }

        return playerIds.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Get longest waiting player
     */
    public Optional<QueueEntry> findLongestWaiting() {
        Set<String> oldest = redisTemplate.opsForZSet().range(WAIT_ZSET_KEY, 0, 0);
        if (oldest == null || oldest.isEmpty()) {
            return Optional.empty();
        }
        return findById(oldest.iterator().next());
    }

    /**
     * Get MMR distribution
     */
    public Map<Integer, Integer> getMmrDistribution() {
        Map<Integer, Integer> distribution = new TreeMap<>();

        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(MMR_ZSET_KEY, 0, -1);

        if (entries != null) {
            for (ZSetOperations.TypedTuple<String> entry : entries) {
                if (entry.getScore() != null) {
                    int bucket = (entry.getScore().intValue() / 100) * 100;
                    distribution.merge(bucket, 1, Integer::sum);
                }
            }
        }

        return distribution;
    }

    private String serialize(QueueEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize QueueEntry", e);
        }
    }

    private QueueEntry deserialize(String json) {
        try {
            return objectMapper.readValue(json, QueueEntry.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize QueueEntry", e);
        }
    }
}
