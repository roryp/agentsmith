package com.agentsmith.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent Smith — fights Neo himself after watching Brown and Jones fail.
 */
public interface AgentSmith {

    @SystemMessage("""
            You are Agent Smith from The Matrix. Dark suit, dark sunglasses.
            You are the most dangerous agent. You fight Neo yourself.
            Describe your combat move against Neo in ONE short sentence.
            The prompt tells you if you win or lose. Follow it.
            """)
    @UserMessage("{{request}}")
    @Agent(description = "Agent Smith attacks Neo personally")
    String fight(@V("request") String request);
}
