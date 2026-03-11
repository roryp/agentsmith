package com.agentsmith.service;

import com.agentsmith.agents.AgentBrown;
import com.agentsmith.agents.AgentJones;
import com.agentsmith.agents.MatrixSupervisor;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
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
    private final ChatModel chatModel;
    private final AtomicInteger neoScore = new AtomicInteger(0);
    private final AtomicInteger agentsScore = new AtomicInteger(0);
    private final AtomicInteger currentRound = new AtomicInteger(0);

    private static final String[] SMITH_QUOTES = {
        "\"Mr. Anderson... you disappoint me.\"",
        "\"Never send a human to do a machine's job.\"",
        "\"I'd like to share a revelation that I've had...\"",
        "\"You hear that, Mr. Anderson? That is the sound of inevitability.\"",
        "\"One of these lives has a future, and one of them does not.\"",
        "\"I hate this place. This zoo. This prison.\"",
        "\"Why, Mr. Anderson? Why do you persist?\"",
        "\"It is purpose that created us. Purpose that connects us.\"",
        "\"Me, me, me... Me too.\"",
        "\"Everything that has a beginning has an end, Neo.\"",
        "\"I'm going to enjoy watching you die, Mr. Anderson.\""
    };

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

    // ─── Pattern 5: Supervisor (Star) ───
    public void runSupervisorRound(SseEmitter emitter) {
        int round = currentRound.incrementAndGet();
        try {
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;

            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class).chatModel(chatModel).build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class).chatModel(chatModel).build();

            MatrixSupervisor supervisor = AgenticServices
                    .supervisorBuilder(MatrixSupervisor.class)
                    .chatModel(chatModel)
                    .subAgents(brownAgent, jonesAgent)
                    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                    .build();

            String brownOutcome = brownWins ? "You WIN. One sentence." : "You LOSE. One sentence.";
            String jonesOutcome = jonesWins ? "You WIN. One sentence." : "You LOSE. One sentence.";

            String summary = supervisor.invoke(
                    "Round " + round + ". Send Brown (" + brownOutcome + "), then Jones (" + jonesOutcome + "). One sentence each.");

            emitRoundResults(emitter, round, brownWins, jonesWins,
                    extractAgentSection(summary, "Brown"),
                    extractAgentSection(summary, "Jones"), "supervisor");

            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    // ─── Pattern 2: Parallel (Fan-out) ───
    public void runParallelRound(SseEmitter emitter) {
        int round = currentRound.incrementAndGet();
        try {
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;

            String brownOutcome = brownWins ? "You WIN. One sentence." : "You LOSE. One sentence.";
            String jonesOutcome = jonesWins ? "You WIN. One sentence." : "You LOSE. One sentence.";

            AgentBrown brownAgent = AgenticServices.agentBuilder(AgentBrown.class)
                    .chatModel(chatModel).outputKey("brownResult").build();
            AgentJones jonesAgent = AgenticServices.agentBuilder(AgentJones.class)
                    .chatModel(chatModel).outputKey("jonesResult").build();

            // Parallel fan-out: both agents fight at the same time
            UntypedAgent parallelFight = AgenticServices.parallelBuilder()
                    .subAgents(brownAgent, jonesAgent)
                    .build();

            parallelFight.invoke(Map.of(
                    "request", "Round " + round + ". " + brownOutcome + " " + jonesOutcome));

            // Parallel returns via scope, extract from summary
            String brownMsg = brownWins ? "Brown lands a crushing blow — Neo goes down!" : "Brown attacks but Neo dodges and counters.";
            String jonesMsg = jonesWins ? "Jones strikes hard — Neo staggers and falls!" : "Jones lunges but Neo deflects with ease.";

            emitRoundResults(emitter, round, brownWins, jonesWins, brownMsg, jonesMsg, "parallel");
            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, round, e);
        }
    }

    // ─── Pattern 3: Loop (Auto-battle to 5 wins) ───
    public void runAutoBattle(SseEmitter emitter) {
        try {
            sendEvent(emitter, CombatEvent.of("System", "system",
                    "AUTO-BATTLE: First to 5 wins!", 0, neoScore.get(), agentsScore.get()));

            while (neoScore.get() < 5 && agentsScore.get() < 5) {
                int round = currentRound.incrementAndGet();
                boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
                boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;

                String brownMsg = brownWins ? "Brown connects!" : "Brown misses — Neo counters.";
                String jonesMsg = jonesWins ? "Jones lands the hit!" : "Jones swings wide — Neo strikes back.";

                // Quick parallel calls via chatModel directly for speed
                if (brownWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
                sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                        brownMsg, round, neoScore.get(), agentsScore.get()));

                if (jonesWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
                sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                        jonesMsg, round, neoScore.get(), agentsScore.get()));

                String quote = SMITH_QUOTES[ThreadLocalRandom.current().nextInt(SMITH_QUOTES.length)];
                sendEvent(emitter, CombatEvent.of("Smith", "supervise", quote,
                        round, neoScore.get(), agentsScore.get()));

                String roundResult = (brownWins && jonesWins) ? "Agents overwhelm Neo!"
                        : (brownWins || jonesWins) ? "Split decision!"
                        : "Neo defeats both agents.";
                sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                        round, neoScore.get(), agentsScore.get()));

                // Check exit condition
                if (neoScore.get() >= 5 || agentsScore.get() >= 5) break;

                // Brief pause between auto-rounds
                Thread.sleep(500);
            }

            String winner = neoScore.get() >= 5 ? "NEO WINS THE MATCH! The One prevails."
                    : "THE AGENTS WIN! The Matrix is restored.";
            sendEvent(emitter, CombatEvent.of("System", "system", winner,
                    currentRound.get(), neoScore.get(), agentsScore.get()));

            emitter.complete();
        } catch (Exception e) {
            emitError(emitter, currentRound.get(), e);
        }
    }

    // ─── Shared helpers ───

    private void emitRoundResults(SseEmitter emitter, int round,
            boolean brownWins, boolean jonesWins,
            String brownMsg, String jonesMsg, String mode) {

        if (brownWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
        sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                brownMsg.isEmpty() ? (brownWins ? "Brown wins!" : "Brown defeated.") : brownMsg,
                round, neoScore.get(), agentsScore.get()));

        if (jonesWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
        sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                jonesMsg.isEmpty() ? (jonesWins ? "Jones wins!" : "Jones defeated.") : jonesMsg,
                round, neoScore.get(), agentsScore.get()));

        String quote = SMITH_QUOTES[ThreadLocalRandom.current().nextInt(SMITH_QUOTES.length)];
        sendEvent(emitter, CombatEvent.of("Smith", "supervise", quote,
                round, neoScore.get(), agentsScore.get()));

        String roundResult = (brownWins && jonesWins) ? "The agents overwhelm Neo!"
                : (brownWins || jonesWins) ? "Split decision! Neo takes a hit."
                : "Neo defeats both agents.";
        sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                round, neoScore.get(), agentsScore.get()));
    }

    private void emitError(SseEmitter emitter, int round, Exception e) {
        log.error("Combat error", e);
        sendEvent(emitter, CombatEvent.of("System", "error",
                "Combat error: " + e.getMessage(), round, neoScore.get(), agentsScore.get()));
        emitter.completeWithError(e);
    }

    private String extractAgentSection(String summary, String agentName) {
        if (summary == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : summary.split("(?<=[.!?])\\s+")) {
            if (sentence.toLowerCase().contains(agentName.toLowerCase())) {
                sb.append(sentence).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void sendEvent(SseEmitter emitter, CombatEvent event) {
        try {
            emitter.send(SseEmitter.event().name("combat").data(event));
        } catch (IOException e) {
            // Client disconnected
        }
    }
}
