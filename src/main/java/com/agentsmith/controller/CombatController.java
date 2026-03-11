package com.agentsmith.controller;

import com.agentsmith.service.CombatService;
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
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CombatController(CombatService combatService) {
        this.combatService = combatService;
    }

    /** Pattern 1: Sequential — Brown → Jones → Smith one after another */
    @GetMapping(value = "/fight/sequential", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fightSequential() {
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.submit(() -> combatService.runSequentialRound(emitter));
        return emitter;
    }

    /** Pattern 2: Parallel — Brown and Jones fight simultaneously */
    @GetMapping(value = "/fight/parallel", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fightParallel() {
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.submit(() -> combatService.runParallelRound(emitter));
        return emitter;
    }

    /** Pattern 3: Loop — Auto-battle to 5 wins */
    @GetMapping(value = "/fight/auto", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fightAuto() {
        SseEmitter emitter = new SseEmitter(300_000L);
        executor.submit(() -> combatService.runAutoBattle(emitter));
        return emitter;
    }

    /** Default fight — uses sequential */
    @GetMapping(value = "/fight", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fight() {
        return fightSequential();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        combatService.resetScores();
        return Map.of("status", "reset", "neoScore", 0, "agentsScore", 0);
    }

    @GetMapping("/scores")
    public Map<String, Object> scores() {
        return Map.of("neoScore", combatService.getNeoScore(), "agentsScore", combatService.getAgentsScore());
    }
}
