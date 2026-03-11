package com.agentsmith.service;

import java.util.Map;

/**
 * Represents a single combat event streamed to the frontend.
 */
public record CombatEvent(
        String agent,    // "Brown", "Jones", or "Smith"
        String type,     // "attack", "supervise", "result"
        String message,
        int round,
        int neoScore,
        int agentsScore
) {
    public static CombatEvent of(String agent, String type, String message, int round, int neoScore, int agentsScore) {
        return new CombatEvent(agent, type, message, round, neoScore, agentsScore);
    }
}
