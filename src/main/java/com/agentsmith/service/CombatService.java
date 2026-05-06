package com.agentsmith.service;

import com.agentsmith.agents.AgentBrown;
import com.agentsmith.agents.AgentJones;
import com.agentsmith.agents.AgentSmith;
import com.agentsmith.agents.ParallelCombatAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CombatService {

    private static final Logger log = LoggerFactory.getLogger(CombatService.class);

    // Pre-built agents — created ONCE at startup, reused across all requests
    private final AgentBrown brownAgent;
    private final AgentJones jonesAgent;
    private final AgentSmith smithAgent;

    private final AtomicInteger neoScore = new AtomicInteger(0);
    private final AtomicInteger agentsScore = new AtomicInteger(0);
    private final AtomicInteger currentRound = new AtomicInteger(0);

    public CombatService(ChatModel chatModel) {
        // Build agents ONCE — eliminates per-request proxy/builder overhead
        this.brownAgent = AgenticServices.agentBuilder(AgentBrown.class)
                .chatModel(chatModel).outputKey("brownResult").build();
        this.jonesAgent = AgenticServices.agentBuilder(AgentJones.class)
                .chatModel(chatModel).outputKey("jonesResult").build();
        this.smithAgent = AgenticServices.agentBuilder(AgentSmith.class)
                .chatModel(chatModel).outputKey("smithResult").build();
    }

    public void resetScores() {
        neoScore.set(0);
        agentsScore.set(0);
        currentRound.set(0);
    }

    public int getNeoScore() { return neoScore.get(); }
    public int getAgentsScore() { return agentsScore.get(); }

    // Agent win chances: normal vs "The One" mode
    private int brownChance(boolean theOne) { return theOne ? 15 : 60; }
    private int jonesChance(boolean theOne) { return theOne ? 10 : 55; }
    private int smithChance(boolean theOne) { return theOne ? 20 : 65; }

    private boolean[] rollOutcomes(boolean theOne) {
        return new boolean[] {
            ThreadLocalRandom.current().nextInt(100) < brownChance(theOne),
            ThreadLocalRandom.current().nextInt(100) < jonesChance(theOne),
            ThreadLocalRandom.current().nextInt(100) < smithChance(theOne)
        };
    }

    private String prompt(int round, boolean wins) {
        return "Round " + round + ". " + (wins ? "You WIN." : "You LOSE.") + " One sentence.";
    }

    private String fallbackMessage(String agent, boolean wins, String result) {
        if (result != null && !result.isBlank()) return result;
        return wins ? agent + " lands a devastating blow on Neo!"
                    : "Neo overpowers " + agent + "!";
    }

    private void scoreAndEmit(SseEmitter emitter, String agent, boolean wins, String result, int round) {
        if (wins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
        sendEvent(emitter, CombatEvent.of(agent, wins ? "attack-win" : "attack",
                fallbackMessage(agent, wins, result), round, neoScore.get(), agentsScore.get()));
    }

    // ─── Pattern 1: Sequential (Chain) via AgenticServices.sequenceBuilder() ───
    // Brown → Jones → Smith, each output flows to the next via AgenticScope shared state
    public void runSequentialRound(SseEmitter emitter, boolean theOne) {
        int round = currentRound.incrementAndGet();
        try {
            boolean[] outcomes = rollOutcomes(theOne);
            boolean brownWins = outcomes[0], jonesWins = outcomes[1], smithWins = outcomes[2];

            if (theOne) sendProgress(emitter, "Neo is THE ONE. Agents don't stand a chance.", round);
            sendProgress(emitter, "Sequential workflow via sequenceBuilder(): Brown → Jones → Smith (each output → next input)...", round);

            // agentAction: prepare Brown's prompt in scope
            var setupBrown = AgenticServices.agentAction(scope -> {
                scope.writeState("request", prompt(round, brownWins));
                sendProgress(emitter, "Agent Brown → LLM call...", round);
            });

            // agentAction: score Brown's fight, then prepare Jones's prompt with Brown's output
            var scoreBrownSetupJones = AgenticServices.agentAction(scope -> {
                String brownResult = scope.readState("brownResult", "");
                scoreAndEmit(emitter, "Brown", brownWins, brownResult, round);
                scope.writeState("request",
                        "Previous agent Brown's result: " + brownResult + ". " + prompt(round, jonesWins));
                sendProgress(emitter, "Agent Jones → LLM call (receiving Brown's output)...", round);
            });

            // agentAction: score Jones's fight, then prepare Smith's prompt with Jones's output
            var scoreJonesSetupSmith = AgenticServices.agentAction(scope -> {
                String jonesResult = scope.readState("jonesResult", "");
                scoreAndEmit(emitter, "Jones", jonesWins, jonesResult, round);
                scope.writeState("request",
                        "Previous agent Jones's result: " + jonesResult + ". " + prompt(round, smithWins));
                sendProgress(emitter, "Agent Smith → LLM call (receiving Jones's output)...", round);
            });

            // Build sequence using AgenticServices.sequenceBuilder() — proper LangChain4j agentic pattern
            UntypedAgent sequentialCombat = AgenticServices
                    .sequenceBuilder()
                    .subAgents(setupBrown, brownAgent, scoreBrownSetupJones, jonesAgent, scoreJonesSetupSmith, smithAgent)
                    .build();

            // Execute the sequence — AgenticScope handles state passing automatically
            sequentialCombat.invoke(Map.of("request", prompt(round, brownWins)));

            // Score Smith's fight and emit final result
            scoreAndEmit(emitter, "Smith", smithWins, null, round);

            int agentWins = (brownWins ? 1 : 0) + (jonesWins ? 1 : 0) + (smithWins ? 1 : 0);
            String resultMsg;
            if (agentWins == 0) {
                resultMsg = "Neo wins all 3 fights! The One cannot be stopped.";
            } else {
                StringBuilder winners = new StringBuilder();
                if (brownWins) winners.append("Brown");
                if (jonesWins) { if (winners.length() > 0) winners.append(" & "); winners.append("Jones"); }
                if (smithWins) { if (winners.length() > 0) winners.append(" & "); winners.append("Smith"); }
                resultMsg = "Neo loses. " + winners + " beat him — fight over.";
            }
            sendEvent(emitter, CombatEvent.of("Neo", "result",
                    resultMsg, round, neoScore.get(), agentsScore.get()));
            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    // ─── Pattern 2: Parallel (Fan-out) — All three fight simultaneously via parallelBuilder() ───
    public void runParallelRound(SseEmitter emitter, boolean theOne) {
        int round = currentRound.incrementAndGet();
        try {
            boolean[] outcomes = rollOutcomes(theOne);
            boolean brownWins = outcomes[0], jonesWins = outcomes[1], smithWins = outcomes[2];

            if (theOne) sendProgress(emitter, "Neo is THE ONE. Agents don't stand a chance.", round);
            sendProgress(emitter, "Parallel fan-out: 3 simultaneous LLM calls via parallelBuilder()...", round);

            // Single call fans out to all three sub-agents in parallel
            String fanOutPrompt = "Round " + round
                    + ". Agent Brown: " + (brownWins ? "WIN" : "LOSE")
                    + ". Agent Jones: " + (jonesWins ? "WIN" : "LOSE")
                    + ". Agent Smith: " + (smithWins ? "WIN" : "LOSE")
                    + ". One sentence.";

            // Build parallel workflow in-method — consistent with sequential/loop patterns
            ParallelCombatAgent parallelAgent = AgenticServices
                .parallelBuilder(ParallelCombatAgent.class)
                .subAgents(brownAgent, jonesAgent, smithAgent)
                .outputKey("combatResults")
                .output(scope -> Map.of(
                        "brown", scope.readState("brownResult", ""),
                        "jones", scope.readState("jonesResult", ""),
                        "smith", scope.readState("smithResult", "")))
                .build();
            Map<String, String> results = parallelAgent.fightAll(fanOutPrompt);
            sendProgress(emitter, "All 3 responses received.", round);

            scoreAndEmit(emitter, "Brown", brownWins, results.get("brown"), round);
            scoreAndEmit(emitter, "Jones", jonesWins, results.get("jones"), round);
            scoreAndEmit(emitter, "Smith", smithWins, results.get("smith"), round);

            int agentWins = (brownWins ? 1 : 0) + (jonesWins ? 1 : 0) + (smithWins ? 1 : 0);
            String resultMsg;
            if (agentWins == 0) {
                resultMsg = "Neo wins all 3 fights! The One cannot be stopped.";
            } else {
                // Build who defeated Neo
                StringBuilder winners = new StringBuilder();
                if (brownWins) winners.append("Brown");
                if (jonesWins) { if (winners.length() > 0) winners.append(" & "); winners.append("Jones"); }
                if (smithWins) { if (winners.length() > 0) winners.append(" & "); winners.append("Smith"); }
                resultMsg = "Neo loses. " + winners + " beat him — fight over.";
            }
            sendEvent(emitter, CombatEvent.of("Neo", "result",
                    resultMsg, round, neoScore.get(), agentsScore.get()));
            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    private void sendProgress(SseEmitter emitter, String message, int round) {
        sendEvent(emitter, CombatEvent.of("System", "progress", message, round, neoScore.get(), agentsScore.get()));
    }

    private void emitError(SseEmitter emitter, int round, Exception e) {
        log.error("Combat error", e);
        sendEvent(emitter, CombatEvent.of("System", "error",
                "Combat error: " + e.getMessage(), round, neoScore.get(), agentsScore.get()));
        emitter.completeWithError(e);
    }

    private void sendEvent(SseEmitter emitter, CombatEvent event) {
        try {
            emitter.send(SseEmitter.event().name("combat").data(event));
        } catch (IOException e) {
            // Client disconnected
        }
    }
}
