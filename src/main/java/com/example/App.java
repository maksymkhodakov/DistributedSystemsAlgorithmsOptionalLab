package com.example;

import java.io.EOFException;
import java.util.List;

/**
 * Lab 3: Luby's algorithm
 * Input (optional; for testing):
 *   N M
 *   u1 v1
 *   ...
 *   uM vM
 * If no input is provided, runs a demo graph.
 * Args:
 *   --threads=K   number of worker threads (default: availableProcessors, min 2)
 *   --seed=S      deterministic seed (default: 42)
 *   --verbose     print per-stage logs
 */
public class App {

    public static void main(String[] args) throws Exception {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        long seed = 42L;

        for (String a : args) {
            if (a.startsWith("--threads=")) {
                threads = Integer.parseInt(a.substring("--threads=".length()));
            }
            else if (a.startsWith("--seed=")){
                seed = Long.parseLong(a.substring("--seed=".length()));
            }
        }

        FastScanner fs = new FastScanner(System.in);

        try {
            int n = fs.nextInt();
            int m = fs.nextInt();
            Graph g = new Graph(n);
            for (int i = 0; i < m; i++) {
                int u = fs.nextInt();
                int v = fs.nextInt();
                g.addEdge(u, v);
            }
            runSolver(g, threads, seed);
        } catch (EOFException noInput) {
            // Demo graph: 6 nodes, 7 edges
            Graph g = new Graph(6);
            g.addEdge(1, 2);
            g.addEdge(2, 3);
            g.addEdge(3, 4);
            g.addEdge(4, 5);
            g.addEdge(5, 6);
            g.addEdge(1, 6);
            g.addEdge(2, 5);

            runSolver(g, threads, seed);
        }
    }

    private static void runSolver(Graph g,
                                  int threads,
                                  long seed) {
        LubyMIS solver = new LubyMIS(g, threads, seed);
        MISResult res = solver.solve();

        System.out.println("========================================");
        System.out.println("Luby MIS (Maximal Independent Set)");
        System.out.println("Graph: N=" + g.n() + ", M=" + g.m());
        System.out.println("Threads=" + threads + ", Seed=" + seed);
        System.out.println("----------------------------------------");
        System.out.println("MIS_size=" + res.getMis().size());
        System.out.println("MIS_nodes=" + res.getMis());
        System.out.println("----------------------------------------");
        System.out.println("stages=" + res.getStages());
        System.out.println("rounds=" + res.getRounds() + " (3 per stage)");
        System.out.println("messages=" + res.getMessages() + " (approx. neighbor inspections/notifications)");
        System.out.println("========================================");

        // throws if MIS is not independent)
        verifyIndependent(g, res.getMis());
    }

    /**
     * Sanity check: verify no edge has both endpoints in MIS.
     */
    private static void verifyIndependent(final Graph g,
                                          final List<Integer> mis) {
        boolean[] in = new boolean[g.n() + 1];
        for (int u : mis) in[u] = true;

        for (int u = 1; u <= g.n(); u++) {
            for (int v : g.neighbors(u)) {
                if (u < v && in[u] && in[v]) {
                    throw new IllegalStateException("Not independent: edge (" + u + "," + v + ") inside MIS");
                }
            }
        }
    }
}
