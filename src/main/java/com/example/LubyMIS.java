package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Luby's algorithm for MIS (Maximal Independent Set) in a general graph.
 * Stage-based, each stage has 3 rounds:
 *  Round 1: each ACTIVE node picks random priority val[u] and "sends" to neighbors
 *  Round 2: winners = nodes with (val,id) greater than all ACTIVE neighbors
 *  Round 3: remove winners and all their neighbors; add winners to MIS
 * Multi-threading:
 *  - each round is processed in parallel blocks of vertices
 *  - Phaser enforces strict barrier between rounds (synchronous distributed rounds)
 * Deterministic randomness:
 *  - val[u] is computed via a deterministic hash of (seed, stage, u)
 *  - results do NOT depend on thread scheduling
 */
public class LubyMIS {

    private final Graph g;
    private final int n;
    private final int threads;
    private final long seed;

    private final ExecutorService pool;
    private final Phaser phaser;

    // ACTIVE status: 1 = active, 0 = removed from induced subgraph
    private final AtomicIntegerArray active;

    // Per-stage markers
    private final AtomicIntegerArray winner; // 1 if winner in this stage
    private final AtomicIntegerArray remove; // 1 if removed at end of this stage

    // Round 1 priorities
    private final double[] val;

    // Metrics: "messages" ~ neighbor inspections + notifications
    private final AtomicLong messages = new AtomicLong(0);

    public LubyMIS(final Graph g,
                   final int threads,
                   final long seed) {
        this.g = g;
        this.n = g.n();
        this.threads = Math.max(1, threads);
        this.seed = seed;

        this.pool = Executors.newFixedThreadPool(this.threads);
        this.phaser = new Phaser(1); // main thread registered
        this.active = new AtomicIntegerArray(n + 1);
        this.winner = new AtomicIntegerArray(n + 1);
        this.remove = new AtomicIntegerArray(n + 1);
        this.val = new double[n + 1];

        for (int u = 1; u <= n; u++) {
            active.set(u, 1);
        }
    }

    public MISResult solve() {
        ArrayList<Integer> mis = new ArrayList<>();

        int stages = 0;
        int rounds = 0;

        while (anyActive()) {
            stages++;

            // clear per-stage arrays
            clearFlags(winner);
            clearFlags(remove);

            // ---------- Round 1 ----------
            final int stageNo = stages;
            runParallel(u -> runRound1(u, stageNo));
            rounds++;

            // ---------- Round 2 ----------
            runParallel(this::runRound2);
            rounds++;

            // ---------- Round 3 ----------
            runParallel(this::runRound3);
            rounds++;

            // Apply stage effects (single thread for clean MIS list)
            ArrayList<Integer> winnersList = new ArrayList<>();
            ArrayList<Integer> removedList = new ArrayList<>();

            for (int u = 1; u <= n; u++) {
                if (active.get(u) == 1 && winner.get(u) == 1) {
                    mis.add(u);
                    winnersList.add(u);
                }
            }

            for (int u = 1; u <= n; u++) {
                if (remove.get(u) == 1 && active.get(u) == 1) {
                    active.set(u, 0);
                    removedList.add(u);
                }
            }

            Collections.sort(winnersList);
            Collections.sort(removedList);
            List<Integer> activeNow = snapshotActive();
            System.out.println("[Stage " + stages + "]");
            System.out.println("  winners(I')=" + winnersList);
            System.out.println("  removed=" + removedList);
            System.out.println("  active_left=" + activeNow.size() + " " + activeNow);
            System.out.println("  MIS_so_far=" + mis.size() + " " + sortedCopy(mis));
            System.out.println();
        }

        pool.shutdownNow();
        Collections.sort(mis);
        return new MISResult(mis, stages, rounds, messages.get());
    }

    // winners notify neighbors => remove winners + all their neighbors
    private void runRound3(int u) {
        if (active.get(u) == 0) return;

        if (winner.get(u) == 1) {
            remove.set(u, 1);
            for (int v : g.neighbors(u)) {
                if (active.get(v) == 1) {
                    remove.set(v, 1);
                    messages.incrementAndGet(); // "winner notifies neighbor"
                }
            }
        }
    }

    // choose winners: local maxima among active neighbors using tie-break by id
    private void runRound2(int u) {
        if (active.get(u) == 0) return;

        boolean isWinner = true;
        double vu = val[u];

        for (final int v : g.neighbors(u)) {
            if (active.get(v) == 0) {
                continue;
            }

            messages.incrementAndGet(); // "received neighbor value"
            double vv = val[v];

            // lose if neighbor has higher (val,id)
            if (vv > vu || (vv == vu && v > u)) {
                isWinner = false;
                break;
            }
        }

        winner.set(u, isWinner ? 1 : 0);
    }

    // each active node chooses a random priority val[u]
    private void runRound1(int u,
                           int stageNo) {
        if (active.get(u) == 1) {
            val[u] = deterministicVal(seed, stageNo, u);
        } else {
            val[u] = -1.0;
        }
    }

    // -------------------- Helpers --------------------

    private boolean anyActive() {
        for (int u = 1; u <= n; u++) {
            if (active.get(u) == 1) return true;
        }
        return false;
    }

    private static void clearFlags(AtomicIntegerArray arr) {
        for (int i = 0; i < arr.length(); i++) arr.set(i, 0);
    }

    private List<Integer> snapshotActive() {
        ArrayList<Integer> list = new ArrayList<>();
        for (int u = 1; u <= n; u++) {
            if (active.get(u) == 1) list.add(u);
        }
        return list;
    }

    private static List<Integer> sortedCopy(List<Integer> list) {
        ArrayList<Integer> cp = new ArrayList<>(list);
        Collections.sort(cp);
        return cp;
    }

    /**
     * Parallel round: split vertices [1..n] into blocks and run task.
     * Phaser barrier ensures strict round synchronization.
     */
    private void runParallel(IntTask task) {
        int block = (n + threads - 1) / threads;

        for (int t = 0; t < threads; t++) {
            final int start = 1 + t * block;
            final int end = Math.min(n, (t + 1) * block);
            if (start > end) continue;

            phaser.register();
            pool.execute(() -> {
                try {
                    for (int u = start; u <= end; u++) {
                        task.apply(u);
                    }
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        phaser.arriveAndAwaitAdvance();
    }

    @FunctionalInterface
    private interface IntTask {
        void apply(int u);
    }

    /**
     * Deterministic "random" in [0,1) based on (seed, stage, vertex).
     * Uses a 64-bit mixing function and takes top 53 bits for a double.
     * This makes results reproducible and independent of thread scheduling.
     */
    static double deterministicVal(long seed,
                                   int stage,
                                   int u) {
        final long C1 = 0x9E3779B97F4A7C15L;
        final long C2 = 0xBF58476D1CE4E5B9L;

        long x = seed;
        x ^= stage * C1;
        x ^= u * C2;

        long m = mix64(x);

        // convert to double in [0,1): use 53 highest bits
        long top53 = (m >>> 11) & ((1L << 53) - 1);
        return top53 / (double) (1L << 53);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return z;
    }
}
