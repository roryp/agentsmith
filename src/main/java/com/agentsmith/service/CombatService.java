package com.agentsmith.service;

import com.agentsmith.agents.AgentBrown;
import com.agentsmith.agents.AgentJones;
import com.agentsmith.agents.MatrixSupervisor;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
        "\"The great and powerful Oracle... we meet at last.\"",
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

    /**
     * Runs a combat round using the LangChain4j Agentic supervisor pattern.
     * Smith (supervisor) coordinates Brown and Jones (sub-agents) to fight Neo.
     * Random outcomes determine who wins each fight.
     */
    public void runCombatRound(SseEmitter emitter) {
        int round = currentRound.incrementAndGet();

        try {
            // Random outcomes: ~30% chance each agent wins
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;

            String brownOutcome = brownWins
                    ? "You WIN. One sentence: your winning move."
                    : "You LOSE. One sentence: your move and how Neo beats you.";
            String jonesOutcome = jonesWins
                    ? "You WIN. One sentence: your winning move."
                    : "You LOSE. One sentence: your move and how Neo beats you.";

            // Build Brown agent via AgenticServices
            AgentBrown brownAgent = AgenticServices
                    .agentBuilder(AgentBrown.class)
                    .chatModel(chatModel)
                    .build();

            // Build Jones agent via AgenticServices
            AgentJones jonesAgent = AgenticServices
                    .agentBuilder(AgentJones.class)
                    .chatModel(chatModel)
                    .build();

            // Listener that streams agent events to the frontend via SSE
            AgentListener sseListener = new AgentListener() {
                @Override
                public void afterAgentInvocation(AgentResponse response) {
                    String agentName = response.agentName();
                    String output = response.output() != null ? response.output().toString() : "";

                    // Skip supervisor internal planning invocations
                    if (agentName.equals("invoke") || output.isEmpty()) return;

                    log.info("Agent '{}' responded: {}", agentName, output.substring(0, Math.min(80, output.length())));
                }

                @Override
                public boolean inheritedBySubagents() {
                    return true;
                }
            };

            // Smith as supervisor coordinates Brown and Jones
            MatrixSupervisor smithSupervisor = AgenticServices
                    .supervisorBuilder(MatrixSupervisor.class)
                    .chatModel(chatModel)
                    .subAgents(brownAgent, jonesAgent)
                    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                    .listener(sseListener)
                    .build();

            // Invoke the supervisor — Smith deploys Brown and Jones
            String smithSummary = smithSupervisor.invoke(
                    "Round " + round + ". Send Brown to fight Neo (" + brownOutcome + "). "
                    + "Send Jones to fight Neo (" + jonesOutcome + "). "
                    + "Keep all responses to ONE sentence each.");

            log.info("Smith summary: {}", smithSummary != null ? smithSummary.substring(0, Math.min(100, smithSummary.length())) : "null");

            // Now score based on random outcomes and send SSE events
            // Brown's fight
            if (brownWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            String brownMsg = extractAgentSection(smithSummary, "Brown");
            sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                    brownMsg.isEmpty() ? smithSummary : brownMsg,
                    round, neoScore.get(), agentsScore.get()));

            // Jones's fight
            if (jonesWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            String jonesMsg = extractAgentSection(smithSummary, "Jones");
            sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                    jonesMsg.isEmpty() ? "Jones engaged Neo in combat." : jonesMsg,
                    round, neoScore.get(), agentsScore.get()));

            // Smith's movie quote
            String quote = SMITH_QUOTES[ThreadLocalRandom.current().nextInt(SMITH_QUOTES.length)];
            sendEvent(emitter, CombatEvent.of("Smith", "supervise", quote,
                    round, neoScore.get(), agentsScore.get()));

            // Round result
            String roundResult;
            if (brownWins && jonesWins) {
                roundResult = "The agents overwhelm Neo! Even The One can fall.";
            } else if (brownWins || jonesWins) {
                roundResult = "Split decision! Neo takes a hit but fights on.";
            } else {
                roundResult = "Neo defeats both agents. The One cannot be stopped.";
            }
            sendEvent(emitter, CombatEvent.of("Neo", "result", roundResult,
                    round, neoScore.get(), agentsScore.get()));

            emitter.complete();
        } catch (Exception e) {
            log.error("Combat error", e);
            sendEvent(emitter, CombatEvent.of("System", "error",
                    "Combat error: " + e.getMessage(),
                    round, neoScore.get(), agentsScore.get()));
            emitter.completeWithError(e);
        }
    }

    /** Try to extract the section about a specific agent from the summary. */
    private String extractAgentSection(String summary, String agentName) {
        if (summary == null) return "";
        // Simple heuristic: find sentences mentioning the agent
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
