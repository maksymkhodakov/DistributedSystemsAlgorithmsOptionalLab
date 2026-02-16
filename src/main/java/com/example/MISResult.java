package com.example;

import java.util.List;

/**
 * Result of LubyMIS:
 *  - mis: one maximal independent set produced by Luby
 *  - stages: number of stages executed
 *  - rounds: stages * 3 (because each stage has 3 rounds)
 *  - messages: approximate "message" count (neighbor inspections + winner notifications)
 */
public class MISResult {
    private final List<Integer> mis;
    private final int stages;
    private final int rounds;
    private final long messages;

    public MISResult(List<Integer> mis, int stages, int rounds, long messages) {
        this.mis = mis;
        this.stages = stages;
        this.rounds = rounds;
        this.messages = messages;
    }

    public List<Integer> getMis() {
        return mis;
    }

    public int getStages() {
        return stages;
    }

    public int getRounds() {
        return rounds;
    }

    public long getMessages() {
        return messages;
    }
}
