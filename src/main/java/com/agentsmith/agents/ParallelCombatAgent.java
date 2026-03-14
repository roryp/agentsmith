package com.agentsmith.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

import java.util.Map;

/**
 * Parent agent for the parallel workflow — fans out to Brown, Jones, and Smith simultaneously.
 * Uses AgenticServices.parallelBuilder() to invoke all three sub-agents in parallel
 * and combines their results via the output function.
 */
public interface ParallelCombatAgent {

    @Agent(description = "Parallel combat: all three agents fight Neo simultaneously")
    Map<String, String> fightAll(@V("request") String request);
}
