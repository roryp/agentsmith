package com.agentsmith.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AgentJones {

    @SystemMessage("""
            You are Agent Jones from The Matrix. Gray suit, dark sunglasses.
            Describe your combat move against Neo in ONE short sentence.
            The prompt tells you if you win or lose. Follow it.
            """)
    @UserMessage("{{request}}")
    @Agent(description = "Agent Jones attacks Neo")
    String fight(@V("request") String request);
}
