package com.matchmaking.algorithm;

import com.matchmaking.model.Player;
import com.matchmaking.model.QueueEntry;
import com.matchmaking.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class MmrWindowFinderTest {
    private MmrWindowFinder windowFinder;

    @BeforeEach
    void setUp() {
        windowFinder = new MmrWindowFinder();
    }

    @Test
    void findBestWindow_returnsEmpty_whenNotEnoughPlayers() {
        List<QueueEntry> entries = createEntries(5);

        Optional<MmrWindowFinder.WindowResult> result = windowFinder.findBestWindow(entries, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void findBestWindow_returnsAllPlayers_whenExactlyWindowSize() {
        List<QueueEntry> entries = createEntries(10);

        Optional<MmrWindowFinder.WindowResult> result = windowFinder.findBestWindow(entries, 10);

        assertThat(result).isPresent();
        assertThat(result.get().entries()).hasSize(10);
    }

    @Test
    void findBestWindow_selectsWindowWithSmallestSpread() {
        List<QueueEntry> entries = new ArrayList<>();
        entries.add(createEntry("p1", 1000));
        entries.add(createEntry("p2", 1010));
        entries.add(createEntry("p3", 1020));
        entries.add(createEntry("p4", 1030));
        entries.add(createEntry("p5", 1040));
        entries.add(createEntry("p6", 1500));
        entries.add(createEntry("p7", 1510));
        entries.add(createEntry("p8", 1520));
        entries.add(createEntry("p9", 1530));
        entries.add(createEntry("p10", 1540));
        entries.add(createEntry("p11", 2000));
        entries.add(createEntry("p12", 2500));

        Optional<MmrWindowFinder.WindowResult> result = windowFinder.findBestWindow(entries, 10);

        assertThat(result).isPresent();
        assertThat(result.get().mmrSpread()).isLessThanOrEqualTo(540);
    }

    @Test
    void findBestWindow_sortsByMmr() {
        List<QueueEntry> entries = new ArrayList<>();
        entries.add(createEntry("p1", 1500));
        entries.add(createEntry("p2", 1000));
        entries.add(createEntry("p3", 1200));

        Optional<MmrWindowFinder.WindowResult> result = windowFinder.findBestWindow(entries, 3);

        assertThat(result).isPresent();
        List<Integer> mmrs = result.get().entries().stream()
                .map(e -> e.player().mmr())
                .toList();
        assertThat(mmrs).containsExactly(1000, 1200, 1500);
    }

    private List<QueueEntry> createEntries(int count) {
        List<QueueEntry> entries = new ArrayList<>();
        Role[] roles = Role.values();
        for (int i = 0; i < count; i++) {
            Role primary = roles[i % 5];
            Role secondary = roles[(i + 1) % 5];
            Player player = Player.create("p" + i, "Player" + i, 1500 + i * 10, primary, secondary);
            entries.add(QueueEntry.create(player, Instant.now()));
        }
        return entries;
    }

    private QueueEntry createEntry(String id, int mmr) {
        Role primary = Role.values()[Math.abs(id.hashCode()) % 5];
        Role secondary = Role.values()[(Math.abs(id.hashCode()) + 1) % 5];
        Player player = Player.create(id, "Player" + id, mmr, primary, secondary);
        return QueueEntry.create(player, Instant.now());
    }
}
