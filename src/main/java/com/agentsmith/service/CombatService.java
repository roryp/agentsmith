package com.agentsmith.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CombatService {

    private static final Logger log = LoggerFactory.getLogger(CombatService.class);
    private final ChatModel chatModel;
    private final AtomicInteger neoScore = new AtomicInteger(0);
    private final AtomicInteger agentsScore = new AtomicInteger(0);
    private final AtomicInteger currentRound = new AtomicInteger(0);

    private static final String BROWN_SYSTEM = """
            You are Agent Brown from The Matrix. You wear a brown suit and dark sunglasses.
            You are skilled in combat but Neo is The One.
            Describe your dramatic combat move in 2-3 short sentences.
            Use Matrix-themed moves: kung fu, bullet time, superhuman leaps, gunplay.
            Neo ALWAYS dodges or counters your move and defeats you. Describe the move AND how Neo beats you.
            Keep it punchy, cinematic, and fun. Never break character.""";

    private static final String JONES_SYSTEM = """
            You are Agent Jones from The Matrix. You wear a gray suit and dark sunglasses.
            You are skilled in combat but Neo is The One.
            Describe your dramatic combat move in 2-3 short sentences.
            Use Matrix-themed moves: kung fu, bullet time, body-takeover, gunplay, martial arts.
            Neo ALWAYS dodges or counters your move and defeats you. Describe the move AND how Neo beats you.
            Keep it punchy, cinematic, and fun. Never break character.""";

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

    public void runCombatRound(SseEmitter emitter) {
        int round = currentRound.incrementAndGet();

        try {
            String prompt = "Round " + round + ": Attack Neo with a unique Matrix combat move!";

            // Brown attacks
            String brownResult = chatModel.chat("You are Agent Brown from The Matrix movie. Describe in 2 sentences how you tried to catch Neo but he ran away. Be dramatic and fun.");
            log.info("Brown result: '{}'", brownResult);
            neoScore.incrementAndGet();
            sendEvent(emitter, CombatEvent.of("Brown", "attack", brownResult,
                    round, neoScore.get(), agentsScore.get()));

            // Jones attacks
            String jonesResult = chatModel.chat(JONES_SYSTEM + "\n\nUser: " + prompt);
            neoScore.incrementAndGet();
            sendEvent(emitter, CombatEvent.of("Jones", "attack", jonesResult,
                    round, neoScore.get(), agentsScore.get()));

            // Smith supervises
            String smithPrompt = "You are Agent Smith from The Matrix. You wear a dark suit and sunglasses. "
                    + "You are the supervisor of Agent Brown and Agent Jones. "
                    + "Give sardonic commentary expressing frustration that Neo keeps winning. Stay in character.\n\n"
                    + "Round " + round + " report: Brown's result: " + brownResult
                    + " Jones's result: " + jonesResult
                    + " Give your 2-3 sentence commentary and threaten to handle Neo yourself next time.";
            String smithCommentary = chatModel.chat(smithPrompt);
            sendEvent(emitter, CombatEvent.of("Smith", "supervise", smithCommentary,
                    round, neoScore.get(), agentsScore.get()));

            // Neo wins
            sendEvent(emitter, CombatEvent.of("Neo", "result",
                    "Neo defeats both agents. The One cannot be stopped.",
                    round, neoScore.get(), agentsScore.get()));

            emitter.complete();
        } catch (Exception e) {
            sendEvent(emitter, CombatEvent.of("System", "error",
                    "Combat error: " + e.getMessage(),
                    round, neoScore.get(), agentsScore.get()));
            emitter.completeWithError(e);
        }
    }

    private void sendEvent(SseEmitter emitter, CombatEvent event) {
        try {
            emitter.send(SseEmitter.event().name("combat").data(event));
        } catch (IOException e) {
            // Client disconnected
        }
    }
}
