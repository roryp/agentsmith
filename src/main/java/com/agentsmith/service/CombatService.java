package com.agentsmith.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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

    private static final String BROWN_WIN = """
            You are Agent Brown from The Matrix. You wear a brown suit and dark sunglasses.
            Describe your dramatic combat move in 2-3 short sentences.
            Use Matrix-themed moves: kung fu, bullet time, superhuman leaps, gunplay.
            This time YOU WIN! Neo is caught off guard and you land a devastating hit. Describe the move AND how you defeat Neo.
            Keep it punchy, cinematic, and fun. Never break character.""";

    private static final String BROWN_LOSE = """
            You are Agent Brown from The Matrix. You wear a brown suit and dark sunglasses.
            Describe your dramatic combat move in 2-3 short sentences.
            Use Matrix-themed moves: kung fu, bullet time, superhuman leaps, gunplay.
            Neo dodges or counters your move and defeats you. Describe the move AND how Neo beats you.
            Keep it punchy, cinematic, and fun. Never break character.""";

    private static final String JONES_WIN = """
            You are Agent Jones from The Matrix. You wear a gray suit and dark sunglasses.
            Describe your dramatic combat move in 2-3 short sentences.
            Use Matrix-themed moves: kung fu, bullet time, body-takeover, gunplay, martial arts.
            This time YOU WIN! Neo underestimates you and you land a crushing blow. Describe the move AND how you defeat Neo.
            Keep it punchy, cinematic, and fun. Never break character.""";

    private static final String JONES_LOSE = """
            You are Agent Jones from The Matrix. You wear a gray suit and dark sunglasses.
            Describe your dramatic combat move in 2-3 short sentences.
            Use Matrix-themed moves: kung fu, bullet time, body-takeover, gunplay, martial arts.
            Neo dodges or counters your move and defeats you. Describe the move AND how Neo beats you.
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

            // ~30% chance each agent wins their fight
            boolean brownWins = ThreadLocalRandom.current().nextInt(100) < 30;
            boolean jonesWins = ThreadLocalRandom.current().nextInt(100) < 30;

            // Brown attacks
            String brownSystem = brownWins ? BROWN_WIN : BROWN_LOSE;
            String brownResult = chatModel.chat(brownSystem + "\n\nUser: " + prompt);
            log.info("Brown wins={}, result: '{}'", brownWins, brownResult);
            if (brownWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Brown", brownWins ? "attack-win" : "attack",
                    brownResult, round, neoScore.get(), agentsScore.get()));

            // Jones attacks
            String jonesSystem = jonesWins ? JONES_WIN : JONES_LOSE;
            String jonesResult = chatModel.chat(jonesSystem + "\n\nUser: " + prompt);
            if (jonesWins) { agentsScore.incrementAndGet(); } else { neoScore.incrementAndGet(); }
            sendEvent(emitter, CombatEvent.of("Jones", jonesWins ? "attack-win" : "attack",
                    jonesResult, round, neoScore.get(), agentsScore.get()));

            // Smith supervises
            String smithContext = brownWins && jonesWins ? "Both Brown and Jones won this round! Celebrate and gloat."
                    : brownWins || jonesWins ? "One agent won, one lost. Mixed results."
                    : "Both agents lost again. Express frustration and threaten to handle Neo yourself.";
            String smithPrompt = "You are Agent Smith from The Matrix. Dark suit, sunglasses. "
                    + "Supervisor of Brown and Jones. Stay in character.\n\n"
                    + "Round " + round + ": Brown " + (brownWins ? "WON" : "LOST") + ": " + brownResult
                    + " Jones " + (jonesWins ? "WON" : "LOST") + ": " + jonesResult
                    + " " + smithContext + " Give 2-3 sentence sardonic commentary.";
            String smithCommentary = chatModel.chat(smithPrompt);
            sendEvent(emitter, CombatEvent.of("Smith", "supervise", smithCommentary,
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
