package com.agentsmith.service;

import com.agentsmith.agents.AgentBrown;
import com.agentsmith.agents.AgentJones;
import com.agentsmith.agents.AgentSmith;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CombatService {

    private static final Logger log = LoggerFactory.getLogger(CombatService.class);
    private final ChatModel chatModel;
    private final AtomicInteger neoScore = new AtomicInteger(0);
    private final AtomicInteger agentsScore = new AtomicInteger(0);
    private final AtomicInteger currentRound = new AtomicInteger(0);

    public CombatService(ChatModel chatModel) {
        this.chatModel = chatModel;
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

    // ─── Pattern 1: Sequential (Chain) — Brown → Jones → Smith one after another ───
    public void runSequentialRound(SseEmitter emitter, boolean theOne) {
        int round = currentRound.incrementAndGet();
        try {
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < brownChance(theOne);
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < jonesChance(theOne);
            boolean smithWins = ThreadLocalRandom.current().nextInt(100) < smithChance(theOne);

            if (theOne) sendProgress(emitter, "Neo is THE ONE. Agents don't stand a chance.", round);

            sendProgress(emitter, "Building agents via AgenticServices.agentBuilder()...", round);
            // Build all three agents via AgenticServices
            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class).chatModel(chatModel).build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class).chatModel(chatModel).build();
            AgentSmith smithAgent = AgenticServices.agentBuilder(AgentSmith.class).chatModel(chatModel).build();
            sendProgress(emitter, "Agents initialized. Starting sequential chain...", round);

            // Sequential: Brown fights first
            sendProgress(emitter, "Agent Brown → LLM call (Azure OpenAI)...", round);
            String brownPrompt = "Round " + round + ". " + (brownWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String brownResult = brownAgent.fight(brownPrompt);
            if (brownWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                    brownResult, round, neoScore.get(), agentsScore.get()));

            // Then Jones
            sendProgress(emitter, "Agent Jones → LLM call (Azure OpenAI)...", round);
            String jonesPrompt = "Round " + round + ". " + (jonesWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String jonesResult = jonesAgent.fight(jonesPrompt);
            if (jonesWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                    jonesResult, round, neoScore.get(), agentsScore.get()));

            // Then Smith fights Neo himself
            sendProgress(emitter, "Agent Smith → LLM call (Azure OpenAI)...", round);
            String smithPrompt = "Round " + round + ". " + (smithWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String smithResult = smithAgent.fight(smithPrompt);
            if (smithWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Smith", smithWins ? "attack-win" : "attack",
                    smithResult, round, neoScore.get(), agentsScore.get()));

            // Round result
            int agentWins = (brownWins ? 1 : 0) + (jonesWins ? 1 : 0) + (smithWins ? 1 : 0);
            int neoWins = 3 - agentWins;
            String roundResult = agentWins == 3 ? "Agents win all 3 fights! Neo is overwhelmed."
                    : agentWins == 0 ? "Neo wins all 3 fights! The One cannot be stopped."
                    : "Neo wins " + neoWins + " of 3, Agents win " + agentWins + " of 3.";
            sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                    round, neoScore.get(), agentsScore.get()));

            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    // ─── Pattern 2: Parallel (Fan-out) — All three fight at the same time via AgenticServices ───
    public void runParallelRound(SseEmitter emitter, boolean theOne) {
        int round = currentRound.incrementAndGet();
        try {
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < brownChance(theOne);
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < jonesChance(theOne);
            boolean smithWins = ThreadLocalRandom.current().nextInt(100) < smithChance(theOne);

            if (theOne) sendProgress(emitter, "Neo is THE ONE. Agents don't stand a chance.", round);

            sendProgress(emitter, "Building agents via AgenticServices.agentBuilder()...", round);
            // Build agents via AgenticServices
            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class).chatModel(chatModel).build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class).chatModel(chatModel).build();
            AgentSmith smithAgent = AgenticServices.agentBuilder(AgentSmith.class).chatModel(chatModel).build();
            sendProgress(emitter, "Agents initialized. Fan-out: 3 parallel LLM calls...", round);

            // All three fight in parallel (we call them concurrently)
            String brownPrompt = "Round " + round + ". " + (brownWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String jonesPrompt = "Round " + round + ". " + (jonesWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String smithPrompt = "Round " + round + ". " + (smithWins ? "You WIN." : "You LOSE.") + " One sentence.";

            sendProgress(emitter, "Brown + Jones + Smith → LLM calls (Azure OpenAI) in parallel...", round);
            var brownFuture = CompletableFuture.supplyAsync(() -> brownAgent.fight(brownPrompt));
            var jonesFuture = CompletableFuture.supplyAsync(() -> jonesAgent.fight(jonesPrompt));
            var smithFuture = CompletableFuture.supplyAsync(() -> smithAgent.fight(smithPrompt));

            // Wait for all to complete
            String brownResult = brownFuture.join();
            String jonesResult = jonesFuture.join();
            String smithResult = smithFuture.join();
            sendProgress(emitter, "All 3 LLM responses received. Processing results...", round);

            if (brownWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                    brownResult, round, neoScore.get(), agentsScore.get()));

            if (jonesWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                    jonesResult, round, neoScore.get(), agentsScore.get()));

            if (smithWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Smith", smithWins ? "attack-win" : "attack",
                    smithResult, round, neoScore.get(), agentsScore.get()));

            int agentWins = (brownWins ? 1 : 0) + (jonesWins ? 1 : 0) + (smithWins ? 1 : 0);
            int neoWins = 3 - agentWins;
            String roundResult = agentWins == 3 ? "Agents win all 3 fights! Neo is overwhelmed."
                    : agentWins == 0 ? "Neo wins all 3 fights! The One cannot be stopped."
                    : "Neo wins " + neoWins + " of 3, Agents win " + agentWins + " of 3.";
            sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                    round, neoScore.get(), agentsScore.get()));

            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    // ─── Pattern 3: Loop (Auto-battle to 5 round-wins) via AgenticServices agents ───
    public void runAutoBattle(SseEmitter emitter, boolean theOne) {
        try {
            // Reset scores for a fresh auto-battle
            resetScores();

            if (theOne) sendProgress(emitter, "Neo has realized he is THE ONE. Agent win chances drastically reduced.", 0);

            sendProgress(emitter, "Building agents via AgenticServices.agentBuilder()...", 0);
            // Build agents once, reuse across rounds
            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class).chatModel(chatModel).build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class).chatModel(chatModel).build();
            AgentSmith smithAgent = AgenticServices.agentBuilder(AgentSmith.class).chatModel(chatModel).build();
            sendProgress(emitter, "Agents initialized. ChatModel: Azure OpenAI (DefaultAzureCredential)", 0);

            sendEvent(emitter, CombatEvent.of("System", "system",
                    "AUTO-BATTLE: First to 5 round wins!", 0, neoScore.get(), agentsScore.get()));

            while (neoScore.get() < 5 && agentsScore.get() < 5) {
                int round = currentRound.incrementAndGet();
                // Agent-favored: 3 agents vs 1 Neo, agents should win more often
                boolean brownWins = ThreadLocalRandom.current().nextInt(100) < brownChance(theOne);
                boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < jonesChance(theOne);
                boolean smithWins = ThreadLocalRandom.current().nextInt(100) < smithChance(theOne);

                sendProgress(emitter, "Round " + round + ": Dispatching 3 parallel LLM calls...", round);

                // Fire all three agents in parallel for speed
                String bp = "Round " + round + ". " + (brownWins ? "You WIN." : "You LOSE.") + " One sentence.";
                String jp = "Round " + round + ". " + (jonesWins ? "You WIN." : "You LOSE.") + " One sentence.";
                String sp = "Round " + round + ". " + (smithWins ? "You WIN." : "You LOSE.") + " One sentence.";

                var bf = CompletableFuture.supplyAsync(() -> brownAgent.fight(bp));
                var jf = CompletableFuture.supplyAsync(() -> jonesAgent.fight(jp));
                var sf = CompletableFuture.supplyAsync(() -> smithAgent.fight(sp));

                sendProgress(emitter, "Round " + round + ": Waiting for Azure OpenAI responses...", round);
                String brownResult = bf.join();
                String jonesResult = jf.join();
                String smithResult = sf.join();
                sendProgress(emitter, "Round " + round + ": All responses received. Evaluating combat...", round);

                // Show individual sub-fight results (no score change yet)
                sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                        brownResult, round, neoScore.get(), agentsScore.get()));

                sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                        jonesResult, round, neoScore.get(), agentsScore.get()));

                sendEvent(emitter, CombatEvent.of("Smith", smithWins ? "attack-win" : "attack",
                        smithResult, round, neoScore.get(), agentsScore.get()));

                // Round-based scoring: majority of 3 sub-fights wins the round (1 point)
                int agentSubWins = (brownWins ? 1 : 0) + (jonesWins ? 1 : 0) + (smithWins ? 1 : 0);
                if (agentSubWins >= 2) {
                    agentsScore.incrementAndGet();
                } else {
                    neoScore.incrementAndGet();
                }

                int neoSubWins = 3 - agentSubWins;
                String roundResult = agentSubWins == 3 ? "Agents win all 3 fights! Agents win the round!"
                        : agentSubWins == 0 ? "Neo wins all 3 fights! Neo wins the round!"
                        : "Neo wins " + neoSubWins + " of 3, Agents win " + agentSubWins + " of 3. "
                            + (agentSubWins >= 2 ? "Agents win the round!" : "Neo wins the round!");
                sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                        round, neoScore.get(), agentsScore.get()));

                if (neoScore.get() >= 5 || agentsScore.get() >= 5) break;
            }

            String winner = neoScore.get() >= 5 ? "NEO WINS THE MATCH!"
                    : "THE AGENTS WIN! The Matrix is restored.";
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
