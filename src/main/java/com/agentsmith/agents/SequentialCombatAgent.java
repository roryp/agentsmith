package com.agentsmith.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

import java.util.Map;

/**
 * Parent agent for the sequential workflow — chains Brown → Jones → Smith one after another.
 * Uses AgenticServices.sequenceBuilder() to invoke agents in order, where each agent's
 * output flows to the next via AgenticScope shared state.
 */
public interface SequentialCombatAgent {

    @Agent(description = "Sequential combat: agents fight Neo one after another, each output feeds to the next")
    Map<String, String> fightSequence(@V("request") String request);
}
