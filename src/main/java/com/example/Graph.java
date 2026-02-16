package com.example;

import java.util.ArrayList;
import java.util.List;

/**
 * Undirected graph with vertices 1..N stored as adjacency lists.
 * Luby's algorithm needs:
 *  - iterate neighbors(u)
 *  - know N and M
 */
public final class Graph {

    private final int n;
    private int m;
    private final List<List<Integer>> adj;

    public Graph(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        this.n = n;
        this.m = 0;
        this.adj = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) adj.add(new ArrayList<>());
    }

    public int n() {
        return n;
    }

    public int m() {
        return m;
    }

    /**
     * Add an undirected edge (u,v).
     */
    public void addEdge(int u, int v) {
        if (u < 1 || u > n || v < 1 || v > n) {
            throw new IllegalArgumentException("Vertices must be in range 1..N");
        }
        if (u == v) return;

        adj.get(u).add(v);
        adj.get(v).add(u);
        m++;
    }

    public List<Integer> neighbors(int u) {
        return adj.get(u);
    }
}
