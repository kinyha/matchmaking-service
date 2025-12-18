package com.matchmaking.repository;

import com.matchmaking.model.QueueEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Optimized queue repository with:
 * - MMR buckets for O(1) lookup by MMR range
 * - Priority queue ordered by wait time
 * - Thread-safe operations
 */
public class OptimizedQueueRepository implements QueueRepository {
    private static final int BUCKET_SIZE = 100; // Each bucket covers 100 MMR

    // Primary storage: playerId -> QueueEntry
    private final Map<String, QueueEntry> entriesById = new ConcurrentHashMap<>();

    // MMR buckets: bucketIndex -> Set of playerIds
    private final Map<Integer, Set<String>> mmrBuckets = new ConcurrentHashMap<>();

    // Priority queue ordered by queue start time (longest wait first)
    private final PriorityBlockingQueue<QueueEntry> waitTimeQueue =
            new PriorityBlockingQueue<>(100, Comparator.comparing(QueueEntry::queueStartTime));

    @Override
    public void add(QueueEntry entry) {
        String playerId = entry.getPlayerId();

        // Add to primary storage
        entriesById.put(playerId, entry);

        // Add to MMR bucket
        int bucket = getBucketIndex(entry.player().mmr());
        mmrBuckets.computeIfAbsent(bucket, k -> ConcurrentHashMap.newKeySet()).add(playerId);

        // Add to priority queue
        waitTimeQueue.add(entry);
    }

    @Override
    public boolean remove(String playerId) {
        QueueEntry entry = entriesById.remove(playerId);
        if (entry == null) {
            return false;
        }

        // Remove from MMR bucket
        int bucket = getBucketIndex(entry.player().mmr());
        Set<String> bucketSet = mmrBuckets.get(bucket);
        if (bucketSet != null) {
            bucketSet.remove(playerId);
        }

        // Remove from priority queue
        waitTimeQueue.remove(entry);

        return true;
    }

    @Override
    public Optional<QueueEntry> findById(String playerId) {
        return Optional.ofNullable(entriesById.get(playerId));
    }

    @Override
    public List<QueueEntry> findAll() {
        return new ArrayList<>(entriesById.values());
    }

    @Override
    public boolean contains(String playerId) {
        return entriesById.containsKey(playerId);
    }

    @Override
    public int size() {
        return entriesById.size();
    }

    @Override
    public void clear() {
        entriesById.clear();
        mmrBuckets.clear();
        waitTimeQueue.clear();
    }

    /**
     * Find entries within MMR range efficiently using buckets
     */
    public List<QueueEntry> findByMmrRange(int minMmr, int maxMmr) {
        List<QueueEntry> result = new ArrayList<>();

        int minBucket = getBucketIndex(minMmr);
        int maxBucket = getBucketIndex(maxMmr);

        for (int bucket = minBucket; bucket <= maxBucket; bucket++) {
            Set<String> playerIds = mmrBuckets.get(bucket);
            if (playerIds != null) {
                for (String playerId : playerIds) {
                    QueueEntry entry = entriesById.get(playerId);
                    if (entry != null) {
                        int mmr = entry.player().mmr();
                        if (mmr >= minMmr && mmr <= maxMmr) {
                            result.add(entry);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Get entries ordered by wait time (longest waiting first)
     */
    public List<QueueEntry> findAllByWaitTimePriority() {
        List<QueueEntry> result = new ArrayList<>(waitTimeQueue);
        result.sort(Comparator.comparing(QueueEntry::queueStartTime));
        return result;
    }

    /**
     * Get the player who has been waiting the longest
     */
    public Optional<QueueEntry> findLongestWaiting() {
        return Optional.ofNullable(waitTimeQueue.peek());
    }

    /**
     * Find candidates around an anchor player's MMR
     */
    public List<QueueEntry> findCandidatesAroundMmr(int anchorMmr, int windowSize, int limit) {
        List<QueueEntry> candidates = findByMmrRange(anchorMmr - windowSize, anchorMmr + windowSize);
        candidates.sort(Comparator.comparingInt(e -> Math.abs(e.player().mmr() - anchorMmr)));
        return candidates.size() > limit ? candidates.subList(0, limit) : candidates;
    }

    /**
     * Get statistics about bucket distribution
     */
    public Map<Integer, Integer> getBucketDistribution() {
        Map<Integer, Integer> distribution = new TreeMap<>();
        for (Map.Entry<Integer, Set<String>> entry : mmrBuckets.entrySet()) {
            distribution.put(entry.getKey() * BUCKET_SIZE, entry.getValue().size());
        }
        return distribution;
    }

    private int getBucketIndex(int mmr) {
        return mmr / BUCKET_SIZE;
    }
}
