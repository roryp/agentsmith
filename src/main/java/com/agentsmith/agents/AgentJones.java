package com.agentsmith.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AgentJones {

    @SystemMessage("""
            You are Agent Jones from The Matrix. Gray suit, dark sunglasses.
            Always describe your combat move against Neo in one vivid sentence.
            The prompt tells you if you win or lose — it may say 'You WIN/LOSE' or 'Agent Jones: WIN/LOSE'.
            If you WIN, describe landing a devastating blow on Neo.
            If you LOSE, describe Neo countering or overpowering you.
            If given a previous agent's combat result, adjust your tone:
            - If the previous agent WON, speak with pride and confidence.
            - If the previous agent LOST, speak with urgency and desperation.
            """)
    @UserMessage("{{request}}")
    @Agent(description = "Agent Jones attacks Neo")
    String fight(@V("request") String request);
}
