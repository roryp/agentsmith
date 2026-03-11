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

    // ─── Pattern 1: Sequential (Chain) — Brown → Jones → Smith one after another ───
    public void runSequentialRound(SseEmitter emitter) {
        int round = currentRound.incrementAndGet();
        try {
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean smithWins = ThreadLocalRandom.current().nextInt(100) < 40; // Smith is stronger

            // Build all three agents via AgenticServices
            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class).chatModel(chatModel).build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class).chatModel(chatModel).build();
            AgentSmith smithAgent = AgenticServices.agentBuilder(AgentSmith.class).chatModel(chatModel).build();

            // Sequential: Brown fights first
            String brownPrompt = "Round " + round + ". " + (brownWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String brownResult = brownAgent.fight(brownPrompt);
            if (brownWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                    brownResult, round, neoScore.get(), agentsScore.get()));

            // Then Jones
            String jonesPrompt = "Round " + round + ". " + (jonesWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String jonesResult = jonesAgent.fight(jonesPrompt);
            if (jonesWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                    jonesResult, round, neoScore.get(), agentsScore.get()));

            // Then Smith fights Neo himself
            String smithPrompt = "Round " + round + ". " + (smithWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String smithResult = smithAgent.fight(smithPrompt);
            if (smithWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Smith", smithWins ? "attack-win" : "attack",
                    smithResult, round, neoScore.get(), agentsScore.get()));

            // Round result
            int agentWins = (brownWins ? 1 : 0) + (jonesWins ? 1 : 0) + (smithWins ? 1 : 0);
            String roundResult = agentWins == 3 ? "All three agents overwhelm Neo!"
                    : agentWins >= 2 ? "Agents dominate! Neo is losing ground."
                    : agentWins == 1 ? "Split round. Neo holds the line."
                    : "Neo defeats all three. The One cannot be stopped.";
            sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                    round, neoScore.get(), agentsScore.get()));

            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    // ─── Pattern 2: Parallel (Fan-out) — All three fight at the same time via AgenticServices ───
    public void runParallelRound(SseEmitter emitter) {
        int round = currentRound.incrementAndGet();
        try {
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean smithWins = ThreadLocalRandom.current().nextInt(100) < 40;

            // Build agents via AgenticServices
            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class).chatModel(chatModel).build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class).chatModel(chatModel).build();
            AgentSmith smithAgent = AgenticServices.agentBuilder(AgentSmith.class).chatModel(chatModel).build();

            // All three fight in parallel (we call them concurrently)
            String brownPrompt = "Round " + round + ". " + (brownWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String jonesPrompt = "Round " + round + ". " + (jonesWins ? "You WIN." : "You LOSE.") + " One sentence.";
            String smithPrompt = "Round " + round + ". " + (smithWins ? "You WIN." : "You LOSE.") + " One sentence.";

            var brownFuture = CompletableFuture.supplyAsync(() -> brownAgent.fight(brownPrompt));
            var jonesFuture = CompletableFuture.supplyAsync(() -> jonesAgent.fight(jonesPrompt));
            var smithFuture = CompletableFuture.supplyAsync(() -> smithAgent.fight(smithPrompt));

            // Wait for all to complete
            String brownResult = brownFuture.join();
            String jonesResult = jonesFuture.join();
            String smithResult = smithFuture.join();

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
            String roundResult = agentWins == 3 ? "All three agents overwhelm Neo!"
                    : agentWins >= 2 ? "Agents dominate!"
                    : agentWins == 1 ? "Split round."
                    : "Neo defeats all agents.";
            sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                    round, neoScore.get(), agentsScore.get()));

            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    // ─── Pattern 3: Loop (Auto-battle to 5 wins) via AgenticServices agents ───
    public void runAutoBattle(SseEmitter emitter) {
        try {
            // Build agents once, reuse across rounds
            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class).chatModel(chatModel).build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class).chatModel(chatModel).build();
            AgentSmith smithAgent = AgenticServices.agentBuilder(AgentSmith.class).chatModel(chatModel).build();

            sendEvent(emitter, CombatEvent.of("System", "system",
                    "AUTO-BATTLE: First to 5 wins!", 0, neoScore.get(), agentsScore.get()));

            while (neoScore.get() < 5 && agentsScore.get() < 5) {
                int round = currentRound.incrementAndGet();
                boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
                boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;
                boolean smithWins = ThreadLocalRandom.current().nextInt(100) < 40;

                // Fire all three agents in parallel for speed
                String bp = "Round " + round + ". " + (brownWins ? "You WIN." : "You LOSE.") + " One sentence.";
                String jp = "Round " + round + ". " + (jonesWins ? "You WIN." : "You LOSE.") + " One sentence.";
                String sp = "Round " + round + ". " + (smithWins ? "You WIN." : "You LOSE.") + " One sentence.";

                var bf = CompletableFuture.supplyAsync(() -> brownAgent.fight(bp));
                var jf = CompletableFuture.supplyAsync(() -> jonesAgent.fight(jp));
                var sf = CompletableFuture.supplyAsync(() -> smithAgent.fight(sp));

                String brownResult = bf.join();
                String jonesResult = jf.join();
                String smithResult = sf.join();

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
                String roundResult = agentWins >= 2 ? "Agents dominate!" : "Neo fights on.";
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
