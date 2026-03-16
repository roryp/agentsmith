package com.agentsmith.service;

import com.agentsmith.agents.AgentBrown;
import com.agentsmith.agents.AgentJones;
import com.agentsmith.agents.AgentSmith;
import com.agentsmith.agents.ParallelCombatAgent;
import com.agentsmith.agents.SequentialCombatAgent;
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
    private final ParallelCombatAgent parallelAgent;
    private final ChatModel chatModel;

    private final AtomicInteger neoScore = new AtomicInteger(0);
    private final AtomicInteger agentsScore = new AtomicInteger(0);
    private final AtomicInteger currentRound = new AtomicInteger(0);

    public CombatService(ChatModel chatModel) {
        this.chatModel = chatModel;

        // Build agents ONCE — eliminates per-request proxy/builder overhead
        this.brownAgent = AgenticServices.agentBuilder(AgentBrown.class)
                .chatModel(chatModel).outputKey("brownResult").build();
        this.jonesAgent = AgenticServices.agentBuilder(AgentJones.class)
                .chatModel(chatModel).outputKey("jonesResult").build();
        this.smithAgent = AgenticServices.agentBuilder(AgentSmith.class)
                .chatModel(chatModel).outputKey("smithResult").build();

        // Build parallel workflow ONCE via AgenticServices.parallelBuilder()
        this.parallelAgent = AgenticServices
                .parallelBuilder(ParallelCombatAgent.class)
                .subAgents(brownAgent, jonesAgent, smithAgent)
                .outputKey("combatResults")
                .output(scope -> Map.of(
                        "brown", scope.readState("brownResult", ""),
                        "jones", scope.readState("jonesResult", ""),
                        "smith", scope.readState("smithResult", "")))
                .build();
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

    private String roundResult(boolean brownWins, boolean jonesWins, boolean smithWins) {
        int agentWins = (brownWins ? 1 : 0) + (jonesWins ? 1 : 0) + (smithWins ? 1 : 0);
        int neoWins = 3 - agentWins;
        if (agentWins == 3) return "Agents win all 3 fights! Neo is overwhelmed.";
        if (agentWins == 0) return "Neo wins all 3 fights! The One cannot be stopped.";
        return "Neo wins " + neoWins + " of 3, Agents win " + agentWins + " of 3. One win is enough — agents win the round!";
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

            //generate the parallelAgent in the constructor and reuse the same parallelAgent instance to avoid builder overhead
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

    // ─── Pattern 3: Loop (Auto-battle to 5 round-wins) via AgenticServices.loopBuilder() ───
    public void runAutoBattle(SseEmitter emitter, boolean theOne) {
        try {
            resetScores();
            if (theOne) sendProgress(emitter, "Neo is THE ONE. Agent win chances drastically reduced.", 0);

            sendEvent(emitter, CombatEvent.of("System", "system",
                    "AUTO-BATTLE: First to 5 round wins!", 0, 0, 0));

            // Non-AI agent: setup each round — rolls random outcomes, writes prompt to scope
            var roundSetup = AgenticServices.agentAction(scope -> {
                boolean isTheOne = scope.readState("theOne", false);
                boolean[] outcomes = rollOutcomes(isTheOne);
                int round = scope.readState("round", 0) + 1;
                scope.writeState("round", round);
                scope.writeState("brownWins", outcomes[0]);
                scope.writeState("jonesWins", outcomes[1]);
                scope.writeState("smithWins", outcomes[2]);
                scope.writeState("request", "Round " + round
                        + ". Agent Brown: " + (outcomes[0] ? "WIN" : "LOSE")
                        + ". Agent Jones: " + (outcomes[1] ? "WIN" : "LOSE")
                        + ". Agent Smith: " + (outcomes[2] ? "WIN" : "LOSE")
                        + ". One sentence.");
                sendProgress(emitter, "Loop iteration " + round + ": roundSetup → parallelCombat → roundScorer", round);
            });

            // Non-AI agent: score each round — reads results from scope, updates scores, sends SSE events
            var roundScorer = AgenticServices.agentAction(scope -> {
                boolean bw = scope.readState("brownWins", false);
                boolean jw = scope.readState("jonesWins", false);
                boolean sw = scope.readState("smithWins", false);
                int round = scope.readState("round", 0);
                currentRound.set(round);

                String brownResult = scope.readState("brownResult", "");
                String jonesResult = scope.readState("jonesResult", "");
                String smithResult = scope.readState("smithResult", "");

                sendProgress(emitter, "Round " + round + ": All 3 LLM responses received. Scoring...", round);

                // Emit individual fight results
                sendEvent(emitter, CombatEvent.of("Brown", bw ? "attack-win" : "attack",
                        fallbackMessage("Brown", bw, brownResult), round, neoScore.get(), agentsScore.get()));
                sendEvent(emitter, CombatEvent.of("Jones", jw ? "attack-win" : "attack",
                        fallbackMessage("Jones", jw, jonesResult), round, neoScore.get(), agentsScore.get()));
                sendEvent(emitter, CombatEvent.of("Smith", sw ? "attack-win" : "attack",
                        fallbackMessage("Smith", sw, smithResult), round, neoScore.get(), agentsScore.get()));

                // Any agent win means Neo loses — agents win the round
                int agentSubWins = (bw ? 1 : 0) + (jw ? 1 : 0) + (sw ? 1 : 0);
                if (agentSubWins >= 1) { agentsScore.incrementAndGet(); }
                else { neoScore.incrementAndGet(); }
                scope.writeState("neoScore", neoScore.get());
                scope.writeState("agentsScore", agentsScore.get());

                int neoSubWins = 3 - agentSubWins;
                String result;
                if (agentSubWins == 0) {
                    result = "Neo wins all 3! The One cannot be stopped.";
                } else {
                    StringBuilder winners = new StringBuilder();
                    if (bw) winners.append("Brown");
                    if (jw) { if (winners.length() > 0) winners.append(" & "); winners.append("Jones"); }
                    if (sw) { if (winners.length() > 0) winners.append(" & "); winners.append("Smith"); }
                    result = "Neo loses. " + winners + " beat him — fight over.";
                }
                sendEvent(emitter, CombatEvent.of("Neo", "result", result,
                        round, neoScore.get(), agentsScore.get()));
            });

            // Loop workflow: roundSetup → parallelCombat → roundScorer, repeat until Neo loses or wins 5
            var autoBattleLoop = AgenticServices
                    .loopBuilder()
                    .subAgents(roundSetup, parallelAgent, roundScorer)
                    .maxIterations(10)
                    .testExitAtLoopEnd(true)
                    .exitCondition(scope ->
                            scope.readState("neoScore", 0) >= 5 ||
                            scope.readState("agentsScore", 0) >= 1)  // One loss = game over
                    .build();

            // Run the loop with initial scope state
            autoBattleLoop.invoke(Map.of("theOne", theOne, "neoScore", 0, "agentsScore", 0, "round", 0));

            String winner = agentsScore.get() >= 1
                    ? "NEO LOSES! The Matrix is restored."
                    : "NEO SURVIVES 5 ROUNDS! The One cannot be stopped.";
            sendEvent(emitter, CombatEvent.of("System", "system", winner,
                    currentRound.get(), neoScore.get(), agentsScore.get()));
            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, currentRound.get(), e);
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
