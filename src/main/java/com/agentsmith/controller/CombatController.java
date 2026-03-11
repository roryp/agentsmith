package com.agentsmith.controller;

import com.agentsmith.service.CombatService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class CombatController {

    private final CombatService combatService;
    private final ChatModel chatModel;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CombatController(CombatService combatService, ChatModel chatModel) {
        this.combatService = combatService;
        this.chatModel = chatModel;
    }

    @GetMapping(value = "/fight", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fight() {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout
        executor.submit(() -> combatService.runCombatRound(emitter));
        return emitter;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        combatService.resetScores();
        return Map.of(
                "status", "reset",
                "neoScore", 0,
                "agentsScore", 0
        );
    }

    @GetMapping("/scores")
    public Map<String, Object> scores() {
        return Map.of(
                "neoScore", combatService.getNeoScore(),
                "agentsScore", combatService.getAgentsScore()
        );
    }
}
